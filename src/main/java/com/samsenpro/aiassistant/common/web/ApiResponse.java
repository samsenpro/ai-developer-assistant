package com.samsenpro.aiassistant.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Envoltorio común de todas las respuestas de la API (éxito y error), para que el cliente siempre
 * reciba la misma forma.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        @Schema(example = "2026-09-23T10:15:30Z") Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Instant.now());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(
            @Schema(example = "AI_PROVIDER_TIMEOUT") String code,
            @Schema(example = "The AI provider did not respond in time") String message,
            Map<String, Object> details) {
    }
}
