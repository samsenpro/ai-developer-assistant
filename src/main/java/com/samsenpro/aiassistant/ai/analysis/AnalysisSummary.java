package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.ai.AIOperation;

import java.time.Instant;
import java.util.List;

/** Entrada del historial de análisis (sin el resultado completo). */
public record AnalysisSummary(Long id, AIOperation operation, AnalysisStatus status, boolean cached,
                              List<Long> fileIds, String model, int inputTokens, int outputTokens, String errorCode,
                              Instant createdAt, Instant completedAt) {

    public static AnalysisSummary from(AnalysisResult result) {
        return new AnalysisSummary(result.getId(), result.getOperation(), result.getStatus(), result.isCached(),
                result.getFileIds(), result.getModel(), result.getInputTokens(), result.getOutputTokens(),
                result.getErrorCode(), result.getCreatedAt(), result.getCompletedAt());
    }
}
