package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;

/** Consumo agregado de una operación en un periodo. */
public record OperationUsage(AIOperation operation, long requests, long failedRequests, long inputTokens,
                             long outputTokens, long totalTokens, long averageDurationMs) {

    /** Fila de la consulta JPQL (sum y avg devuelven Long y Double, y pueden ser null). */
    public record Row(AIOperation operation, Long requests, Long failedRequests, Long inputTokens, Long outputTokens,
                      Long totalTokens, Double averageDurationMs) {

        public OperationUsage toUsage() {
            return new OperationUsage(operation, nz(requests), nz(failedRequests), nz(inputTokens), nz(outputTokens),
                    nz(totalTokens), averageDurationMs == null ? 0 : Math.round(averageDurationMs));
        }

        private static long nz(Long value) {
            return value == null ? 0 : value;
        }
    }
}
