package com.samsenpro.aiassistant.ai.context;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límites de contexto que se envía al modelo.
 *
 * @param maxSourceTokens    presupuesto para el código (archivos seleccionados + relacionados) de un prompt
 * @param maxPromptTokens    tamaño máximo de un prompt completo (instrucciones + código + petición)
 * @param charsPerToken      caracteres por token para la estimación (conservador para código)
 * @param maxFilesPerRequest archivos seleccionables en una sola operación
 * @param maxRelatedFiles    archivos relacionados que se pueden añadir automáticamente
 * @param relatedDepth       niveles de dependencias que se siguen (Controller → Service → Repository = 2)
 * @param maxChunks          partes en las que se puede dividir una revisión que no cabe en un prompt
 * @param redactSecrets      enmascarar secretos (claves, tokens, contraseñas) antes de enviar el código
 */
@Validated
@ConfigurationProperties(prefix = "ai.context")
public record ContextProperties(
        @Positive int maxSourceTokens,
        @Positive int maxPromptTokens,
        @DecimalMin("1.0") double charsPerToken,
        @Positive int maxFilesPerRequest,
        @Min(0) int maxRelatedFiles,
        @Min(0) int relatedDepth,
        @Positive int maxChunks,
        boolean redactSecrets) {
}
