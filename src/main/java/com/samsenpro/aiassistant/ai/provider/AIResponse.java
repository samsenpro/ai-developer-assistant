package com.samsenpro.aiassistant.ai.provider;

/**
 * Respuesta en bruto del proveedor, antes de parsear y validar.
 *
 * @param tokensEstimated true si el proveedor no informó del consumo y los tokens son una estimación
 * @param finishReason    motivo de fin que informa el proveedor (p. ej. STOP o LENGTH), o null
 */
public record AIResponse(String content, String provider, String model, int inputTokens, int outputTokens,
                         boolean tokensEstimated, String finishReason) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    /** El proveedor cortó la respuesta al alcanzar el límite de tokens de salida. */
    public boolean truncated() {
        return finishReason != null && ("LENGTH".equalsIgnoreCase(finishReason)
                || "MAX_TOKENS".equalsIgnoreCase(finishReason));
    }

    @Override
    public String toString() {
        return "AIResponse[provider=" + provider + ", model=" + model + ", chars="
                + (content == null ? 0 : content.length()) + ", inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens + ", finishReason=" + finishReason + "]";
    }
}
