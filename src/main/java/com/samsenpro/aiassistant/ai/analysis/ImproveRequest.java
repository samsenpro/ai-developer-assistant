package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.ai.analysis.dto.ImprovementGoal;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Petición de IMPROVE.
 *
 * @param goals objetivos de la mejora: performance, readability, clean-code, security, architecture
 */
public record ImproveRequest(
        @Schema(example = "1") @NotNull Long projectId,
        @Schema(example = "[10]") @NotEmpty List<@NotNull Long> fileIds,
        @Schema(example = "JAVA", nullable = true) ProgrammingLanguage language,
        @Schema(example = "[\"READABILITY\", \"SECURITY\"]") @NotEmpty Set<@NotNull ImprovementGoal> goals,
        @Schema(nullable = true) @Size(max = 2000) String instructions,
        @Schema(example = "true", nullable = true) Boolean includeRelatedFiles) {
}
