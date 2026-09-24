package com.samsenpro.aiassistant.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** DTOs de la API de conversaciones. */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    /**
     * @param fileIds archivos del proyecto que servirán de contexto en toda la conversación
     */
    public record CreateConversationRequest(
            @Schema(example = "Understanding the user module") @NotBlank @Size(max = 200) String title,
            @Schema(example = "[10, 11]") @Size(max = 50) Set<@NotNull Long> fileIds) {
    }

    /**
     * @param fileIds archivos adicionales solo para esta pregunta (opcional)
     */
    public record SendMessageRequest(
            @Schema(example = "Why does this service use this repository?") @NotBlank String content,
            @Schema(example = "[12]", nullable = true) @Size(max = 50) Set<@NotNull Long> fileIds) {
    }

    public record ConversationResponse(Long id, Long projectId, String title, Set<Long> fileIds, Instant createdAt,
                                       Instant updatedAt) {

        static ConversationResponse from(Conversation conversation) {
            return new ConversationResponse(conversation.getId(), conversation.getProjectId(), conversation.getTitle(),
                    Set.copyOf(conversation.getFileIds()), conversation.getCreatedAt(), conversation.getUpdatedAt());
        }
    }

    public record MessageResponse(Long id, Message.Role role, String content, Instant createdAt) {

        static MessageResponse from(Message message) {
            return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
        }
    }

    public record ConversationDetail(ConversationResponse conversation, List<MessageResponse> messages) {
    }

    /**
     * Respuesta a una pregunta, con información sobre el contexto que se usó.
     *
     * @param historyMessagesUsed    mensajes anteriores enviados al modelo
     * @param historyMessagesOmitted mensajes anteriores que no se enviaron (ventana de historial)
     */
    public record ChatReply(MessageResponse userMessage, MessageResponse assistantMessage, List<String> contextFiles,
                            int historyMessagesUsed, int historyMessagesOmitted, List<String> warnings,
                            String provider, String model, int inputTokens, int outputTokens) {
    }
}
