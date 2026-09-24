package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.ai.analysis.dto.DocumentationType;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Petición de GENERATE_DOCUMENTATION. */
public record DocumentationRequest(
        @Schema(example = "1") @NotNull Long projectId,
        @Schema(example = "[10]") @NotEmpty List<@NotNull Long> fileIds,
        @Schema(example = "JAVA", nullable = true) ProgrammingLanguage language,
        @Schema(example = "JAVADOC") @NotNull DocumentationType type,
        @Schema(nullable = true) @Size(max = 2000) String instructions,
        @Schema(example = "true", nullable = true) Boolean includeRelatedFiles) {
}
