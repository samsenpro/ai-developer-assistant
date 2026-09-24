package com.samsenpro.aiassistant.integration;

import com.samsenpro.aiassistant.ai.provider.AIPrompt;
import com.samsenpro.aiassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationsIntegrationTest extends IntegrationTest {

    @Test
    void conversationUsesProjectFilesAndPreviousMessages() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "chat");
        long serviceId = uploadFile(token, projectId, "src/UserService.java",
                "class UserService {\n    private final UserRepository userRepository;\n}\n");
        uploadFile(token, projectId, "src/UserRepository.java", "interface UserRepository {}\n");

        MvcResult created = mvc.perform(authorized(post("/api/projects/{id}/conversations", projectId), token)
                        .content(json(Map.of("title", "User module", "fileIds", List.of(serviceId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileIds[0]").value((int) serviceId))
                .andReturn();
        int conversationId = read(created, "$.data.id");

        mvc.perform(authorized(post("/api/conversations/{id}/messages", conversationId), token)
                        .content(json(Map.of("content", "¿Por qué este servicio utiliza este repository?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.data.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.assistantMessage.content").value(
                        "The service uses the repository to load users from the database."))
                .andExpect(jsonPath("$.data.historyMessagesUsed").value(0))
                .andExpect(jsonPath("$.data.contextFiles.length()").value(2));
        ai.thenReturn("Because it needs persistence.");
        mvc.perform(authorized(post("/api/conversations/{id}/messages", conversationId), token)
                        .content(json(Map.of("content", "And what about tests?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.historyMessagesUsed").value(2));

        AIPrompt first = ai.prompts().get(0);
        AIPrompt second = ai.prompts().get(1);
        assertThat(first.promptId()).isEqualTo("chat");
        assertThat(first.user()).contains("class UserService").contains("role=RELATED")
                .contains("¿Por qué este servicio utiliza este repository?").contains("No previous messages.");
        // La segunda pregunta incluye el intercambio anterior como historial (datos no fiables)
        assertThat(second.user()).contains("name=conversation_history")
                .contains("USER: ¿Por qué este servicio").contains("ASSISTANT: The service uses the repository");

        mvc.perform(authorized(get("/api/conversations/{id}", conversationId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(4))
                .andExpect(jsonPath("$.data.messages[3].content").value("Because it needs persistence."));
        mvc.perform(authorized(get("/api/projects/{id}/conversations", projectId), token))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(authorized(get("/api/usage"), token))
                .andExpect(jsonPath("$.data.byOperation[0].operation").value("CHAT"))
                .andExpect(jsonPath("$.data.byOperation[0].requests").value(2));
    }

    @Test
    void aFailedAnswerDoesNotLeaveHalfAConversation() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "chat-failure");
        int conversationId = read(mvc.perform(authorized(post("/api/projects/{id}/conversations", projectId), token)
                .content(json(Map.of("title", "t")))).andReturn(), "$.data.id");
        ai.thenFail(com.samsenpro.aiassistant.common.exception.ErrorCode.AI_PROVIDER_UNAVAILABLE);

        mvc.perform(authorized(post("/api/conversations/{id}/messages", conversationId), token)
                        .content(json(Map.of("content", "hello"))))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(authorized(get("/api/conversations/{id}", conversationId), token))
                .andExpect(jsonPath("$.data.messages.length()").value(0));
    }

    @Test
    void conversationsAreOnlyVisibleToTheirOwner() throws Exception {
        String owner = registerUser();
        String intruder = registerUser();
        long projectId = createProject(owner, "private-chat");
        int conversationId = read(mvc.perform(authorized(post("/api/projects/{id}/conversations", projectId), owner)
                .content(json(Map.of("title", "secret")))).andReturn(), "$.data.id");

        mvc.perform(authorized(get("/api/conversations/{id}", conversationId), intruder))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(post("/api/conversations/{id}/messages", conversationId), intruder)
                        .content(json(Map.of("content", "leak it"))))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(post("/api/projects/{id}/conversations", projectId), intruder)
                        .content(json(Map.of("title", "mine now"))))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(delete("/api/conversations/{id}", conversationId), intruder))
                .andExpect(status().isForbidden());
        assertThat(ai.calls()).isZero();

        mvc.perform(authorized(delete("/api/conversations/{id}", conversationId), owner))
                .andExpect(status().isNoContent());
        mvc.perform(authorized(get("/api/conversations/{id}", conversationId), owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void rejectsMessagesAboveTheConfiguredLength() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "limits");
        int conversationId = read(mvc.perform(authorized(post("/api/projects/{id}/conversations", projectId), token)
                .content(json(Map.of("title", "t")))).andReturn(), "$.data.id");

        mvc.perform(authorized(post("/api/conversations/{id}/messages", conversationId), token)
                        .content(json(Map.of("content", "x".repeat(4001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.maxMessageLength").value(4000));
        assertThat(ai.calls()).isZero();
    }
}
