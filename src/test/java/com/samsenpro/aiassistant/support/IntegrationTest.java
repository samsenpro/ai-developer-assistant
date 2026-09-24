package com.samsenpro.aiassistant.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los tests de integración: aplicación completa, PostgreSQL real en un contenedor
 * compartido por todas las clases y el proveedor LLM sustituido por {@link FakeAIProvider}
 * (registrado como "scripted"): sin llamadas reales, sin coste y sin Internet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestAIProviderConfig.class)
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        // Un solo contenedor para toda la ejecución (patrón singleton de Testcontainers)
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected FakeAIProvider ai;

    @BeforeEach
    void resetProvider() {
        ai.reset();
        ai.byDefault(prompt -> ai.response(ScriptedResponses.forPrompt(prompt)));
    }

    @TestConfiguration
    static class TestAIProviderConfig {

        @Bean
        FakeAIProvider scriptedAIProvider() {
            return new FakeAIProvider("scripted");
        }
    }

    // --- Helpers ---

    /** Registra un usuario nuevo (nombre único) y devuelve su token. */
    protected String registerUser() throws Exception {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", username + "@example.com",
                                "password", "Str0ngPass1"))))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    protected long createProject(String token, String name) throws Exception {
        MvcResult result = mvc.perform(authorized(post("/api/projects"), token)
                        .content(json(Map.of("name", name, "description", "E-commerce backend", "language", "JAVA"))))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    protected long uploadFile(String token, long projectId, String path, String content) throws Exception {
        MvcResult result = mvc.perform(authorized(post("/api/projects/{id}/files", projectId), token)
                        .content(json(Map.of("path", path, "content", content))))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    protected MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    protected MvcResult getJson(String url, String token, Object... variables) throws Exception {
        return mvc.perform(authorized(get(url, variables), token)).andReturn();
    }
}
