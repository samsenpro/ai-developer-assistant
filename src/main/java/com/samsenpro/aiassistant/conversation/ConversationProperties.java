package com.samsenpro.aiassistant.conversation;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límites de las conversaciones.
 *
 * @param maxMessages        mensajes por conversación (usuario + asistente)
 * @param maxMessageLength   caracteres de un mensaje del usuario
 * @param historyTokenBudget tokens máximos del historial que se envía al modelo
 * @param maxHistoryMessages mensajes anteriores que se consideran como máximo
 * @param maxFiles           archivos de contexto por conversación
 */
@Validated
@ConfigurationProperties(prefix = "ai.conversation")
public record ConversationProperties(@Positive int maxMessages, @Positive int maxMessageLength,
                                     @Positive int historyTokenBudget, @Positive int maxHistoryMessages,
                                     @Positive int maxFiles) {
}
