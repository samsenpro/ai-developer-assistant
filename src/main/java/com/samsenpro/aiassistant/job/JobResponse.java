package com.samsenpro.aiassistant.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AnalysisView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado de un job. Cuando termina con éxito incluye el análisis completo; cuando falla, el código
 * y el mensaje de error (los mismos que habría devuelto la operación síncrona).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(
        @Schema(example = "0f8fad5b-d9cb-469f-a165-70867728950e") UUID jobId,
        AIOperation operation,
        @Schema(example = "PENDING") JobStatus status,
        Long projectId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        @Schema(description = "Análisis resultante (solo con status COMPLETED)") AnalysisView<JsonNode> analysis,
        @Schema(description = "Error (solo con status FAILED)") AnalysisView.ErrorView error) {

    public static JobResponse of(AnalysisJob job, AnalysisView<JsonNode> analysis) {
        AnalysisView.ErrorView error = job.getErrorCode() == null ? null
                : new AnalysisView.ErrorView(job.getErrorCode(), job.getErrorMessage());
        return new JobResponse(job.getId(), job.getOperation(), job.getStatus(), job.getProjectId(), job.getCreatedAt(),
                job.getStartedAt(), job.getCompletedAt(), analysis, error);
    }
}
