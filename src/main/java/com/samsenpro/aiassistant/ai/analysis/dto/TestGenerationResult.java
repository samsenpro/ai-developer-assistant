package com.samsenpro.aiassistant.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Resultado de GENERATE_TESTS. */
public record TestGenerationResult(
        @Schema(description = "Framework de la aplicación identificado en el código (null si no hay ninguno)",
                example = "Spring Boot", nullable = true) String detectedFramework,
        @Schema(example = "JUnit 5") @NotBlank String testFramework,
        @Schema(example = "Mockito", nullable = true) String mockingLibrary,
        @NotEmpty List<@Valid GeneratedTest> tests,
        @Schema(description = "Comportamientos cubiertos por los tests") List<String> testedBehaviors,
        @Schema(description = "Suposiciones hechas al generar los tests") List<String> assumptions) {

    public TestGenerationResult {
        tests = Lists.clean(tests);
        testedBehaviors = Lists.cleanText(testedBehaviors);
        assumptions = Lists.cleanText(assumptions);
    }

    public record GeneratedTest(
            @Schema(example = "UserServiceTest.java") @NotBlank String filename,
            @NotBlank String content,
            String description) {
    }
}
