package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;

/** Datos de una llamada al proveedor para registrar en ai_usage. */
public record UsageRecord(Long userId, Long projectId, AIOperation operation, String provider, String model,
                          int inputTokens, int outputTokens, boolean tokensEstimated, long durationMs,
                          UsageStatus status, String errorCode) {
}
