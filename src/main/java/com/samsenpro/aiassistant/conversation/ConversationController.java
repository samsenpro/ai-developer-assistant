package com.samsenpro.aiassistant.conversation;

import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.PageResponse;
import com.samsenpro.aiassistant.conversation.ConversationDtos.ChatReply;
import com.samsenpro.aiassistant.conversation.ConversationDtos.ConversationDetail;
import com.samsenpro.aiassistant.conversation.ConversationDtos.ConversationResponse;
import com.samsenpro.aiassistant.conversation.ConversationDtos.CreateConversationRequest;
import com.samsenpro.aiassistant.conversation.ConversationDtos.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Conversations", description = "Conversaciones con la IA sobre un proyecto y sus archivos")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/api/projects/{projectId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una conversación", description = "Opcionalmente con archivos del proyecto como contexto.")
    public ApiResponse<ConversationResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long projectId,
                                                    @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.ok(conversationService.create(user.id(), projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/conversations")
    @Operation(summary = "Listar las conversaciones de un proyecto")
    public ApiResponse<PageResponse<ConversationResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @PathVariable Long projectId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(conversationService.list(user.id(), projectId, page, size));
    }

    @GetMapping("/api/conversations/{id}")
    @Operation(summary = "Obtener una conversación con sus mensajes")
    public ApiResponse<ConversationDetail> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.get(user.id(), id));
    }

    @PostMapping("/api/conversations/{id}/messages")
    @Operation(summary = "Enviar un mensaje",
            description = "La respuesta usa los archivos de la conversación, la información del proyecto y una ventana "
                    + "del historial reciente limitada por tokens.")
    public ApiResponse<ChatReply> sendMessage(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                              @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(conversationService.sendMessage(user.id(), id, request));
    }

    @DeleteMapping("/api/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar una conversación")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        conversationService.delete(user.id(), id);
    }
}
