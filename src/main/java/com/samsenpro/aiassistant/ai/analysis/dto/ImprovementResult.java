package com.samsenpro.aiassistant.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Resultado de IMPROVE. Es una propuesta: los archivos del proyecto no se modifican; el usuario
 * revisa el código mejorado y decide si lo aplica (p. ej. con PUT /files/{id}).
 */
public record ImprovementResult(
        @Schema(description = "Análisis del código original") @NotBlank String originalAnalysis,
        List<@Valid SuggestedChange> suggestedChanges,
        @NotEmpty List<@Valid ImprovedFile> improvedCode,
        @NotBlank String explanation) {

    public ImprovementResult {
        suggestedChanges = Lists.clean(suggestedChanges);
        improvedCode = Lists.clean(improvedCode);
    }

    public record SuggestedChange(ImprovementGoal goal, String file, @NotBlank String description, String rationale) {
    }

    public record ImprovedFile(@NotBlank String file, @NotBlank String content) {
    }
}
