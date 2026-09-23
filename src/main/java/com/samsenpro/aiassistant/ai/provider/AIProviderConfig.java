package com.samsenpro.aiassistant.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Infraestructura HTTP del cliente LLM (Spring AI usa estos beans en lugar de los suyos):
 * <ul>
 *     <li>timeouts explícitos: ninguna llamada al proveedor puede quedarse colgada;</li>
 *     <li>reintentos acotados: por defecto Spring AI hace 10 intentos con backoff de hasta 3
 *     minutos, inaceptable en una petición síncrona;</li>
 *     <li>errores HTTP tipados, con el código de estado como dato.</li>
 * </ul>
 */
@Configuration
public class AIProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AIProviderConfig.class);
    private static final int MAX_ERROR_BODY_CHARS = 300;

    /** Aplica a los RestClient.Builder de Spring Boot, que es el que usa el cliente de OpenAI de Spring AI. */
    @Bean
    RestClientCustomizer aiProviderTimeouts(AIProviderProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.timeout());
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }

    /**
     * Solo se reintentan los errores 5xx y los fallos de conexión. Un timeout no se reintenta: si el
     * proveedor ya tardó más que el límite, reintentar duplicaría la espera del usuario.
     */
    @Bean
    RetryTemplate retryTemplate(AIProviderProperties properties) {
        return RetryTemplate.builder()
                .maxAttempts(properties.maxAttempts())
                .exponentialBackoff(Duration.ofMillis(500), 2, Duration.ofSeconds(5))
                .retryOn(AIProviderConfig::isRetryable)
                .withListener(new org.springframework.retry.RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(org.springframework.retry.RetryContext context,
                                                                 org.springframework.retry.RetryCallback<T, E> callback,
                                                                 Throwable throwable) {
                        // Los errores HTTP del proveedor llevan el estado y el cuerpo recortado; del
                        // resto solo se registra el tipo
                        log.warn("AI provider call failed (attempt {}): {}", context.getRetryCount(),
                                throwable instanceof org.springframework.ai.retry.TransientAiException
                                        ? throwable.getMessage() : throwable.getClass().getSimpleName());
                    }
                })
                .build();
    }

    static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof TransientAiException) {
            return true;
        }
        if (throwable instanceof ResourceAccessException) {
            for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException) {
                    return true;
                }
            }
        }
        return false;
    }

    @Bean
    ResponseErrorHandler responseErrorHandler() {
        return new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().isError();
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                int status = response.getStatusCode().value();
                String body = abbreviate(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
                String message = "HTTP " + status + " - " + body;
                if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    throw new ProviderHttpException.RateLimited(message,
                            parseRetryAfter(response.getHeaders().getFirst("Retry-After")));
                }
                if (response.getStatusCode().is4xxClientError()) {
                    throw new ProviderHttpException.ClientError(status, message);
                }
                throw new ProviderHttpException.ServerError(status, message);
            }
        };
    }

    /** El cuerpo de error del proveedor solo se usa para logs internos, recortado. */
    private static String abbreviate(String body) {
        String singleLine = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return singleLine.length() > MAX_ERROR_BODY_CHARS
                ? singleLine.substring(0, MAX_ERROR_BODY_CHARS) + "..." : singleLine;
    }

    static Duration parseRetryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(header.trim())));
        } catch (NumberFormatException ex) {
            // Retry-After también puede ser una fecha HTTP; en ese caso no se propaga
            return null;
        }
    }
}
