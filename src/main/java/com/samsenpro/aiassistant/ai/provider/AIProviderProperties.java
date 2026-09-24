package com.samsenpro.aiassistant.ai.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param primary          nombre del proveedor principal
 * @param fallback         nombre del proveedor de reserva (vacío = sin fallback)
 * @param timeout          tiempo máximo de espera de la respuesta del proveedor
 * @param connectTimeout   tiempo máximo para establecer la conexión
 * @param maxAttempts      intentos por llamada ante errores transitorios (5xx o fallo de conexión)
 * @param structuredAttempts intentos cuando la respuesta estructurada no supera la validación
 * @param openai           proveedor compatible con la API de OpenAI (OpenAI, Ollama, Groq, OpenRouter...)
 */
@Validated
@ConfigurationProperties(prefix = "ai.provider")
public record AIProviderProperties(
        @NotBlank String primary,
        String fallback,
        @NotNull Duration timeout,
        @NotNull Duration connectTimeout,
        @Min(1) int maxAttempts,
        @Min(1) int structuredAttempts,
        @Valid @NotNull OpenAi openai) {

    /**
     * @param name        nombre con el que aparece en métricas y en ai_usage (p. ej. "openai" u "ollama")
     * @param model       modelo por defecto
     * @param temperature temperatura baja: se buscan respuestas precisas y estables, no creativas
     * @param jsonMode    pedir {@code response_format: json_object} en las operaciones estructuradas
     */
    public record OpenAi(@NotBlank String name, @NotBlank String model, double temperature, boolean jsonMode) {
    }

    public boolean hasFallback() {
        return fallback != null && !fallback.isBlank();
    }
}
