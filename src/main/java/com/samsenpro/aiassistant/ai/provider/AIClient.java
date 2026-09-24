package com.samsenpro.aiassistant.ai.provider;

import com.samsenpro.aiassistant.ai.parser.InvalidAIResponseException;
import com.samsenpro.aiassistant.ai.parser.ParsedOutput;
import com.samsenpro.aiassistant.ai.parser.StructuredOutputParser;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.usage.AIMetrics;
import com.samsenpro.aiassistant.usage.AIUsageService;
import com.samsenpro.aiassistant.usage.UsageRecord;
import com.samsenpro.aiassistant.usage.UsageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Punto de entrada único a los proveedores LLM para el resto de la aplicación:
 * <pre>
 * proveedor principal ──fallo del proveedor──▶ proveedor de fallback (si está configurado)
 *        │
 *        ▼
 * respuesta en bruto → StructuredOutputParser → DTO validado
 * </pre>
 * Registra cada llamada (ai_usage + métricas), tenga éxito o no. Los servicios de IA no conocen
 * qué proveedor respondió ni cómo se reintentó.
 */
@Component
public class AIClient {

    private static final Logger log = LoggerFactory.getLogger(AIClient.class);

    private final List<AIProvider> chain;
    private final AIProviderProperties properties;
    private final StructuredOutputParser parser;
    private final AIUsageService usageService;
    private final AIMetrics metrics;

    public AIClient(List<AIProvider> providers, AIProviderProperties properties, StructuredOutputParser parser,
                    AIUsageService usageService, AIMetrics metrics) {
        Map<String, AIProvider> byName = providers.stream()
                .collect(Collectors.toMap(AIProvider::name, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Duplicate AI provider name: " + a.name());
                }));
        List<AIProvider> configured = new ArrayList<>();
        configured.add(resolve(byName, properties.primary()));
        if (properties.hasFallback()) {
            AIProvider fallback = resolve(byName, properties.fallback());
            if (fallback.name().equals(properties.primary())) {
                throw new IllegalStateException("ai.provider.fallback must be different from ai.provider.primary");
            }
            configured.add(fallback);
        }
        this.chain = List.copyOf(configured);
        this.properties = properties;
        this.parser = parser;
        this.usageService = usageService;
        this.metrics = metrics;
        log.info("AI providers configured: primary={} model={} fallback={}", chain.getFirst().name(),
                chain.getFirst().model(), properties.hasFallback() ? properties.fallback() : "none");
    }

    /** Proveedor y modelo principales: forman parte del hash de caché de los resultados. */
    public String primaryIdentity() {
        AIProvider primary = chain.getFirst();
        return primary.name() + ":" + primary.model();
    }

    /** Respuesta en texto libre (conversaciones). */
    public AIResult<String> generate(AIPrompt prompt, AICallContext context) {
        Invocation invocation = invoke(prompt, context, false);
        AIResponse response = invocation.response();
        if (response.content() == null || response.content().isBlank()) {
            record(invocation, context, UsageStatus.INVALID_RESPONSE, ErrorCode.INVALID_AI_RESPONSE);
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.EMPTY_RESPONSE,
                    "The AI provider returned an empty response", List.of());
        }
        record(invocation, context, UsageStatus.SUCCESS, null);
        return new AIResult<>(response.content().strip(), response.provider(), response.model(), response.inputTokens(),
                response.outputTokens(), response.tokensEstimated(), List.of());
    }

    /**
     * Respuesta JSON validada contra {@code type}. Si el modelo devuelve algo no válido se vuelve a
     * pedir hasta {@code ai.provider.structured-attempts} veces (con temperatura > 0 la siguiente
     * respuesta suele ser distinta); los tokens de todos los intentos se contabilizan.
     */
    public <T> AIResult<T> generateStructured(AIPrompt prompt, AICallContext context, Class<T> type) {
        int inputTokens = 0;
        int outputTokens = 0;
        boolean estimated = false;
        for (int attempt = 1; ; attempt++) {
            Invocation invocation = invoke(prompt, context, true);
            AIResponse response = invocation.response();
            inputTokens += response.inputTokens();
            outputTokens += response.outputTokens();
            estimated |= response.tokensEstimated();
            try {
                ParsedOutput<T> parsed = parser.parse(response.content(), type, response.truncated());
                record(invocation, context, UsageStatus.SUCCESS, null);
                return new AIResult<>(parsed.value(), response.provider(), response.model(), inputTokens, outputTokens,
                        estimated, parsed.ignoredFields());
            } catch (InvalidAIResponseException ex) {
                record(invocation, context, UsageStatus.INVALID_RESPONSE, ErrorCode.INVALID_AI_RESPONSE);
                // Una respuesta truncada se repetiría igual: no tiene sentido reintentar
                boolean retry = attempt < properties.structuredAttempts()
                        && ex.reason() != InvalidAIResponseException.Reason.TRUNCATED_RESPONSE;
                log.warn("Invalid AI response for {} (attempt {}/{}): {}{}", context.operation(), attempt,
                        properties.structuredAttempts(), ex.reason(), retry ? ", retrying" : "");
                if (!retry) {
                    throw ex;
                }
            }
        }
    }

    private Invocation invoke(AIPrompt prompt, AICallContext context, boolean structured) {
        ApiException lastFailure = null;
        for (int i = 0; i < chain.size(); i++) {
            AIProvider provider = chain.get(i);
            long start = System.nanoTime();
            try {
                AIResponse response = structured ? provider.generateStructured(prompt) : provider.generate(prompt);
                return new Invocation(response, Duration.ofNanos(System.nanoTime() - start));
            } catch (ApiException ex) {
                recordFailure(provider, context, ex.code(), Duration.ofNanos(System.nanoTime() - start));
                lastFailure = ex;
                if (!ex.code().isProviderFailure() || i == chain.size() - 1) {
                    throw ex;
                }
                log.warn("AI provider {} failed with {}, falling back to {}", provider.name(), ex.code(),
                        chain.get(i + 1).name());
            } catch (RuntimeException ex) {
                // Un proveedor que no respeta el contrato: se trata como no disponible
                log.error("AI provider {} failed unexpectedly", provider.name(), ex);
                recordFailure(provider, context, ErrorCode.AI_PROVIDER_UNAVAILABLE,
                        Duration.ofNanos(System.nanoTime() - start));
                lastFailure = new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE,
                        ErrorCode.AI_PROVIDER_UNAVAILABLE.defaultMessage(), Map.of(), null, ex);
                if (i == chain.size() - 1) {
                    throw lastFailure;
                }
            }
        }
        throw lastFailure;
    }

    private void record(Invocation invocation, AICallContext context, UsageStatus status, ErrorCode error) {
        AIResponse response = invocation.response();
        usageService.record(new UsageRecord(context.userId(), context.projectId(), context.operation(),
                response.provider(), response.model(), response.inputTokens(), response.outputTokens(),
                response.tokensEstimated(), invocation.duration().toMillis(), status,
                error == null ? null : error.name()));
        metrics.recordCall(context.operation(), response.provider(), status, invocation.duration(),
                response.inputTokens(), response.outputTokens());
        if (error != null) {
            metrics.recordFailure(context.operation(), response.provider(), error);
        }
    }

    private void recordFailure(AIProvider provider, AICallContext context, ErrorCode error, Duration duration) {
        usageService.record(new UsageRecord(context.userId(), context.projectId(), context.operation(),
                provider.name(), provider.model(), 0, 0, false, duration.toMillis(), UsageStatus.FAILED, error.name()));
        metrics.recordCall(context.operation(), provider.name(), UsageStatus.FAILED, duration, 0, 0);
        metrics.recordFailure(context.operation(), provider.name(), error);
    }

    private static AIProvider resolve(Map<String, AIProvider> providers, String name) {
        AIProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalStateException("Unknown AI provider '" + name + "'. Available: " + providers.keySet());
        }
        return provider;
    }

    private record Invocation(AIResponse response, Duration duration) {
    }
}
