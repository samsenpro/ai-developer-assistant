package com.samsenpro.aiassistant.ai.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.context.BuiltContext;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Análisis devuelto por la API.
 *
 * @param cached   el resultado se reutilizó de un análisis anterior idéntico (sin llamada al LLM)
 * @param replayed respuesta repetida por Idempotency-Key (la petición ya se había procesado)
 * @param contextFiles archivos que vio el modelo: los seleccionados (PRIMARY) y los que añadió el
 *                     ContextBuilder (RELATED), completos (FULL), resumidos (OUTLINE), por partes
 *                     (PARTIAL) u omitidos por el límite de contexto (OMITTED)
 * @param result   resultado estructurado de la operación (null si falló)
 * @param warnings avisos sobre el contexto o la validación (secretos enmascarados, líneas corregidas...)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisView<T>(
        @Schema(example = "15") Long id,
        @Schema(example = "1") Long projectId,
        AIOperation operation,
        AnalysisStatus status,
        boolean cached,
        boolean replayed,
        List<Long> fileIds,
        List<BuiltContext.ContextFile> contextFiles,
        T result,
        List<String> warnings,
        @Schema(example = "openai") String provider,
        @Schema(example = "gpt-4o-mini") String model,
        @Schema(example = "1.0.0") String promptVersion,
        int inputTokens,
        int outputTokens,
        ErrorView error,
        Instant createdAt,
        Instant completedAt) {

    public record ErrorView(String code, String message) {
    }
}
