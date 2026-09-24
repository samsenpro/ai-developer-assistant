package com.samsenpro.aiassistant.integration;

import com.samsenpro.aiassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los tests desactivan por defecto la exportación de métricas: aquí se activa para comprobar /actuator/prometheus. */
@AutoConfigureObservability
class ObservabilityIntegrationTest extends IntegrationTest {

    @Test
    void healthIsPublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        // Endpoints sensibles no expuestos
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    }

    @Test
    void exposesAiMetricsWithLowCardinalityLabelsOnly() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "metrics");
        long fileId = uploadFile(token, projectId, "src/A.java", "class A {\n    void a() {}\n}\n");
        mvc.perform(authorized(post("/api/ai/explain"), token)
                .content(json(Map.of("projectId", projectId, "fileIds", List.of(fileId))))).andExpect(status().isOk());
        mvc.perform(authorized(post("/api/ai/explain"), token)
                .content(json(Map.of("projectId", projectId, "fileIds", List.of(fileId))))).andExpect(status().isOk());

        String metrics = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics)
                .contains("ai_requests_total{")
                .contains("ai_request_duration_seconds_bucket{")
                .contains("ai_tokens_used_total{")
                .contains("ai_cache_requests_total{")
                .contains("ai_jobs_queued")
                .contains("http_server_requests_seconds")
                .containsPattern("ai_requests_total\\{[^}]*operation=\"explain\"[^}]*provider=\"scripted\"[^}]*status=\"success\"");
        List<String> aiSeries = metrics.lines().filter(line -> line.startsWith("ai_")).toList();
        assertThat(aiSeries).isNotEmpty().noneMatch(line -> line.contains("userId") || line.contains("projectId")
                || line.contains("conversationId") || line.contains("user=") || line.contains("project="));
    }

    @Test
    void propagatesOrGeneratesTheCorrelationId() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-ID", "trace-me-123"))
                .andExpect(header().string("X-Correlation-ID", "trace-me-123"));
        mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-ID"));
        String generated = mvc.perform(get("/actuator/health").header("X-Correlation-ID", "bad value\nwith newline"))
                .andReturn().getResponse().getHeader("X-Correlation-ID");
        assertThat(generated).hasSize(36).doesNotContain("bad");
    }

    @Test
    void openApiDocumentsEveryArea() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/projects/{projectId}/files")))
                .andExpect(jsonPath("$.paths", hasKey("/api/ai/review")))
                .andExpect(jsonPath("$.paths", hasKey("/api/ai/jobs/{jobId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/conversations/{id}/messages")))
                .andExpect(jsonPath("$.paths", hasKey("/api/usage")))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ReviewResult").exists());
    }
}
