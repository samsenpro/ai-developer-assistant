package com.samsenpro.aiassistant.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Resultado de GENERATE_DOCUMENTATION. */
public record DocumentationResult(
        @Schema(example = "Documentation for the user module") @NotBlank String title,
        @NotBlank String summary,
        @NotEmpty List<@Valid Document> documents) {

    public DocumentationResult {
        documents = Lists.clean(documents);
    }

    /**
     * @param file   archivo al que corresponde (null para documentos globales como un README)
     * @param format markdown, java, python...
     */
    public record Document(
            @Schema(example = "src/main/java/com/shop/UserService.java", nullable = true) String file,
            @Schema(example = "markdown") @NotBlank String format,
            @NotBlank String content) {
    }
}
