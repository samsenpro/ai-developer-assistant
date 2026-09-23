package com.samsenpro.aiassistant.ai.provider;

import java.util.List;

/**
 * Resultado validado de una llamada de IA.
 *
 * @param inputTokens   tokens de entrada acumulados de todos los intentos
 * @param ignoredFields campos inesperados que el modelo devolvió y se descartaron
 */
public record AIResult<T>(T value, String provider, String model, int inputTokens, int outputTokens,
                          boolean tokensEstimated, List<String> ignoredFields) {
}
