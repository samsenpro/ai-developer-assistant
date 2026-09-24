package com.samsenpro.aiassistant.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Resultado de EXPLAIN. */
public record ExplanationResult(
        @Schema(example = "UserService manages user registration and lookup.") @NotBlank String summary,
        @Schema(example = "Encapsulates the business rules for creating and finding users.") @NotBlank String purpose,
        List<@Valid Component> structure,
        List<String> responsibilities,
        @Schema(description = "Pasos del flujo principal, en orden") List<String> flow,
        List<@Valid Dependency> dependencies,
        @Schema(description = "Puntos a tener en cuenta (riesgos, decisiones, detalles no evidentes)") List<String> keyPoints) {

    public ExplanationResult {
        structure = Lists.clean(structure);
        responsibilities = Lists.cleanText(responsibilities);
        flow = Lists.cleanText(flow);
        dependencies = Lists.clean(dependencies);
        keyPoints = Lists.cleanText(keyPoints);
    }

    /**
     * @param kind CLASS, INTERFACE, METHOD, FUNCTION, FIELD...
     */
    public record Component(@NotBlank String name, String kind, @NotBlank String description) {
    }

    /**
     * @param type INTERNAL (del proyecto), EXTERNAL (librería) o FRAMEWORK
     */
    public record Dependency(@NotBlank String name, String type, String usage) {
    }
}
