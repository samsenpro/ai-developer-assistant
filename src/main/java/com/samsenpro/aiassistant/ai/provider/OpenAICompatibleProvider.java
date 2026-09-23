package com.samsenpro.aiassistant.ai.provider;

import com.samsenpro.aiassistant.ai.context.TokenEstimator;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Proveedor para cualquier API compatible con la de OpenAI (OpenAI, Ollama, Groq, OpenRouter,
 * DeepSeek...), construido sobre el ChatModel de Spring AI. Cambiar de proveedor compatible es
 * cuestión de configuración: AI_BASE_URL, AI_MODEL y AI_API_KEY.
 * <p>
 * Es el único punto de la aplicación que conoce Spring AI y traduce sus excepciones al contrato de
 * errores de {@link AIProvider}.
 */
@Component
public class OpenAICompatibleProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    private final ChatModel chatModel;
    private final AIProviderProperties.OpenAi properties;
    private final TokenEstimator tokenEstimator;

    public OpenAICompatibleProvider(ChatModel chatModel, AIProviderProperties providerProperties,
                                    TokenEstimator tokenEstimator) {
        this.chatModel = chatModel;
        this.properties = providerProperties.openai();
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public String name() {
        return properties.name();
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public AIResponse generate(AIPrompt prompt) {
        return call(prompt, false);
    }

    @Override
    public AIResponse generateStructured(AIPrompt prompt) {
        return call(prompt, properties.jsonMode());
    }

    private AIResponse call(AIPrompt prompt, boolean jsonMode) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(properties.model())
                .temperature(properties.temperature())
                .maxTokens(prompt.maxOutputTokens());
        if (jsonMode) {
            options.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
        }
        Prompt request = new Prompt(List.of(new SystemMessage(prompt.system()), new UserMessage(prompt.user())),
                options.build());
        try {
            return toResponse(prompt, chatModel.call(request));
        } catch (ProviderHttpException.RateLimited ex) {
            log.warn("AI provider {} rate limited the request: {}", name(), ex.getMessage());
            throw new ApiException(ErrorCode.AI_RATE_LIMIT, "The AI provider is rate limiting requests, try again later",
                    Map.of("source", "PROVIDER"), ex.retryAfter(), ex);
        } catch (ProviderHttpException.ClientError ex) {
            throw clientError(ex);
        } catch (ProviderHttpException.ServerError ex) {
            log.warn("AI provider {} failed with HTTP {} after retries", name(), ex.status());
            throw unavailable(ex);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("AI provider {} timed out", name());
                throw new ApiException(ErrorCode.AI_PROVIDER_TIMEOUT, ErrorCode.AI_PROVIDER_TIMEOUT.defaultMessage(),
                        Map.of(), null, ex);
            }
            log.warn("AI provider {} is not reachable: {}", name(), ex.getMessage());
            throw unavailable(ex);
        } catch (RestClientException ex) {
            // Respuesta 2xx que no es el JSON esperado de la API (proxy intermedio, URL equivocada...)
            log.warn("AI provider {} returned an unreadable response: {}", name(), ex.getClass().getSimpleName());
            throw unavailable(ex);
        }
    }

    private AIResponse toResponse(AIPrompt prompt, ChatResponse response) {
        Generation generation = response == null ? null : response.getResult();
        String content = generation == null || generation.getOutput() == null ? null : generation.getOutput().getText();
        String finishReason = generation == null ? null : generation.getMetadata().getFinishReason();
        String model = response != null && response.getMetadata().getModel() != null
                && !response.getMetadata().getModel().isBlank() ? response.getMetadata().getModel() : properties.model();

        Usage usage = response == null ? null : response.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        boolean estimated = promptTokens == null || promptTokens <= 0;
        // Algunos proveedores compatibles no informan del consumo: se estima para no dejar las
        // cuotas sin efecto, y se marca como estimado en ai_usage
        int inputTokens = estimated ? tokenEstimator.estimate(prompt.system()) + tokenEstimator.estimate(prompt.user())
                : promptTokens;
        int outputTokens = completionTokens != null && completionTokens > 0 ? completionTokens
                : tokenEstimator.estimate(content);
        return new AIResponse(content, name(), model, inputTokens, outputTokens, estimated,
                finishReason == null ? null : finishReason.toUpperCase(Locale.ROOT));
    }

    private ApiException clientError(ProviderHttpException.ClientError ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (ex.status() == 400 && (message.contains("context_length") || message.contains("context length")
                || message.contains("maximum context"))) {
            return new ApiException(ErrorCode.CONTEXT_TOO_LARGE,
                    "The request exceeds the context window of the AI model", Map.of("source", "PROVIDER"), null, ex);
        }
        // 401/403/404 son errores de configuración (API key, modelo, URL): el usuario no puede
        // corregirlos, así que se informa como proveedor no disponible y se registra como error
        log.error("AI provider {} rejected the request with HTTP {}: {}", name(), ex.status(), ex.getMessage());
        return unavailable(ex);
    }

    private ApiException unavailable(Exception cause) {
        return new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE, ErrorCode.AI_PROVIDER_UNAVAILABLE.defaultMessage(),
                Map.of(), null, cause);
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            // SocketTimeoutException es subclase de InterruptedIOException
            if (cause instanceof HttpTimeoutException || cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
