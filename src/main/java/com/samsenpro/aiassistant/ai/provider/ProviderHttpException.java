package com.samsenpro.aiassistant.ai.provider;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.time.Duration;

/**
 * Errores HTTP del proveedor con el código de estado como dato (en lugar de tener que extraerlo
 * del mensaje). Extienden las excepciones de Spring AI para que su RetryTemplate siga distinguiendo
 * entre errores transitorios (se reintentan) y definitivos (no).
 */
public final class ProviderHttpException {

    private ProviderHttpException() {
    }

    /** 429: no se reintenta en caliente, se informa al cliente con Retry-After. */
    public static class RateLimited extends NonTransientAiException {

        private final Duration retryAfter;

        public RateLimited(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    /** Resto de 4xx: petición rechazada (API key inválida, modelo inexistente, contexto excesivo...). */
    public static class ClientError extends NonTransientAiException {

        private final int status;

        public ClientError(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** 5xx: error transitorio del proveedor; se reintenta según {@code ai.provider.max-attempts}. */
    public static class ServerError extends TransientAiException {

        private final int status;

        public ServerError(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
