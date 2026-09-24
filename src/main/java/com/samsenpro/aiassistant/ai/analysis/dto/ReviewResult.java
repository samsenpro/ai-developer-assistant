package com.samsenpro.aiassistant.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Resultado de REVIEW. */
public record ReviewResult(
        @Schema(example = "The service works but has a SQL injection risk and duplicated validation.") @NotBlank String summary,
        List<@Valid Issue> issues,
        @Schema(description = "Recomendaciones generales que no corresponden a un problema concreto") List<String> recommendations) {

    public ReviewResult {
        issues = Lists.clean(issues);
        recommendations = Lists.cleanText(recommendations);
    }

    /**
     * @param file ruta del archivo tal como se envió al modelo
     * @param line línea exacta, o null si el modelo no puede determinarla (nunca inventada: se
     *             valida contra el archivo real)
     */
    public record Issue(
            @Schema(example = "HIGH") @NotNull Severity severity,
            @Schema(example = "SECURITY") @NotNull IssueCategory category,
            @Schema(example = "src/main/java/com/shop/UserService.java") String file,
            @Schema(example = "42", nullable = true) Integer line,
            @Schema(example = "The query is built by concatenating user input.") @NotBlank String description,
            @Schema(example = "Use a parameterized query.") @NotBlank String recommendation) {

        public Issue withLocation(String file, Integer line) {
            return new Issue(severity, category, file, line, description, recommendation);
        }
    }
}
