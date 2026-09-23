package com.samsenpro.aiassistant.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Resultado de ANALYZE_ERROR. Es un diagnóstico probable, no un hecho: por eso lleva un nivel de
 * confianza y el prompt exige lenguaje de hipótesis ("probable cause", "likely issue").
 */
public record ErrorAnalysisResult(
        @Schema(example = "The likely cause is that userRepository.findByEmail returns null for unknown emails.")
        @NotBlank String probableCause,
        @Schema(example = "MEDIUM") @NotNull Confidence confidence,
        @NotBlank String explanation,
        List<String> possibleFixes,
        List<String> prevention,
        @Schema(description = "Otras causas posibles, menos probables") List<String> alternativeCauses) {

    public ErrorAnalysisResult {
        possibleFixes = Lists.cleanText(possibleFixes);
        prevention = Lists.cleanText(prevention);
        alternativeCauses = Lists.cleanText(alternativeCauses);
    }
}
