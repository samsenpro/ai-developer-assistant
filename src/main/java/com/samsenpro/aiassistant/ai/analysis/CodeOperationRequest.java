package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición de EXPLAIN, REVIEW y GENERATE_TESTS.
 *
 * @param language            opcional: si se indica, todos los archivos deben ser de ese lenguaje
 * @param instructions        petición concreta del usuario (opcional), p. ej. "céntrate en la concurrencia"
 * @param includeRelatedFiles añadir automáticamente archivos relacionados como contexto (por defecto sí)
 */
public record CodeOperationRequest(
        @Schema(example = "1") @NotNull Long projectId,
        @Schema(example = "[10]") @NotEmpty List<@NotNull Long> fileIds,
        @Schema(example = "JAVA", nullable = true) ProgrammingLanguage language,
        @Schema(example = "Focus on security issues", nullable = true) @Size(max = 2000) String instructions,
        @Schema(example = "true", nullable = true) Boolean includeRelatedFiles) {
}
