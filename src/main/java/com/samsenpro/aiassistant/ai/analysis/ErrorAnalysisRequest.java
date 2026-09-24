package com.samsenpro.aiassistant.ai.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición de ANALYZE_ERROR. El proyecto y los archivos son opcionales: con ellos el diagnóstico
 * puede apoyarse en el código real.
 */
public record ErrorAnalysisRequest(
        @Schema(example = "1", nullable = true) Long projectId,
        @Schema(example = "[10]", nullable = true) @Size(max = 50) List<@NotNull Long> fileIds,
        @Schema(example = "java.lang.NullPointerException: Cannot invoke \"User.getEmail()\" because \"user\" is null")
        @NotBlank @Size(max = 5000) String error,
        @Schema(example = "Happens when logging in with an email that is not registered", nullable = true)
        @Size(max = 5000) String context,
        @Schema(example = "at com.shop.UserService.login(UserService.java:42)", nullable = true)
        @Size(max = 20000) String stackTrace) {
}
