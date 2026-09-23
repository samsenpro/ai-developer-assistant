package com.samsenpro.aiassistant.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @Schema(example = "my-ecommerce")
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "may contain letters, digits, spaces, '.', '_' and '-'")
        String name,

        @Schema(example = "E-commerce backend")
        @Size(max = 1000)
        String description,

        @Schema(example = "JAVA")
        @NotNull
        ProgrammingLanguage language) {
}
