package com.samsenpro.aiassistant.common.exception;

import java.time.Duration;
import java.util.Map;

/**
 * Excepción de negocio con un código de error estable. Todo lo que se lanza con este tipo llega al
 * cliente con su código y su mensaje; el resto de excepciones se traduce a INTERNAL_ERROR sin
 * detalles.
 * <p>
 * {@code details} lleva datos estructurados y seguros para el cliente (límites, tokens
 * estimados...). {@code retryAfter} se traduce a la cabecera Retry-After.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;
    private final Duration retryAfter;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of(), null, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details, Duration retryAfter,
                        Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.retryAfter = retryAfter;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
