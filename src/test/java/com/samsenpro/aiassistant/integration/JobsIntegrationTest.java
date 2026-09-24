package com.samsenpro.aiassistant.integration;

import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Operaciones largas como jobs asíncronos: PENDING → PROCESSING → COMPLETED/FAILED. */
class JobsIntegrationTest extends IntegrationTest {

    private String token;
    private long projectId;
    private long fileId;

    @BeforeEach
    void setUpProject() throws Exception {
        token = registerUser();
        projectId = createProject(token, "jobs");
        fileId = uploadFile(token, projectId, "src/main/java/com/shop/UserService.java", """
                package com.shop;

                public class UserService {
                    public void save() {
                        repository.save();
                    }
                }
                """);
    }

    @Test
    void submitsAJobAndReturnsTheResultWhenItCompletes() throws Exception {
        MvcResult submitted = mvc.perform(authorized(post("/api/ai/jobs"), token)
                        .content(json(Map.of("operation", "REVIEW", "projectId", projectId, "fileIds", List.of(fileId)))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/ai/jobs/")))
                .andExpect(jsonPath("$.data.jobId").isString())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String jobId = read(submitted, "$.data.jobId");

        String status = awaitTerminal(jobId);

        assertThat(status).isEqualTo("COMPLETED");
        mvc.perform(authorized(get("/api/ai/jobs/{id}", jobId), token))
                .andExpect(jsonPath("$.data.startedAt").isString())
                .andExpect(jsonPath("$.data.completedAt").isString())
                .andExpect(jsonPath("$.data.analysis.operation").value("REVIEW"))
                .andExpect(jsonPath("$.data.analysis.result.issues[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.error").doesNotExist());
        mvc.perform(authorized(get("/api/ai/jobs"), token))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        // El resultado queda en el historial del proyecto
        mvc.perform(authorized(get("/api/projects/{id}/analyses", projectId), token))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }

    @Test
    void aFailingProviderEndsTheJobAsFailedWithTheErrorCode() throws Exception {
        ai.byDefault(prompt -> {
            throw com.samsenpro.aiassistant.support.FakeAIProvider.failure(ErrorCode.AI_PROVIDER_TIMEOUT);
        });

        String jobId = read(mvc.perform(authorized(post("/api/ai/jobs"), token)
                .content(json(Map.of("operation", "EXPLAIN", "projectId", projectId, "fileIds", List.of(fileId)))))
                .andExpect(status().isAccepted()).andReturn(), "$.data.jobId");

        assertThat(awaitTerminal(jobId)).isEqualTo("FAILED");
        mvc.perform(authorized(get("/api/ai/jobs/{id}", jobId), token))
                .andExpect(jsonPath("$.data.error.code").value("AI_PROVIDER_TIMEOUT"))
                .andExpect(jsonPath("$.data.analysis").doesNotExist());
    }

    @Test
    void theSameIdempotencyKeyReturnsTheExistingJob() throws Exception {
        String body = json(Map.of("operation", "ANALYZE_ERROR", "error", "NullPointerException at line 5"));

        String first = read(mvc.perform(authorized(post("/api/ai/jobs"), token).content(body)
                .header("Idempotency-Key", "job-key-000001")).andExpect(status().isAccepted()).andReturn(), "$.data.jobId");
        String second = read(mvc.perform(authorized(post("/api/ai/jobs"), token).content(body)
                .header("Idempotency-Key", "job-key-000001")).andExpect(status().isOk()).andReturn(), "$.data.jobId");

        assertThat(second).isEqualTo(first);
        awaitTerminal(first);
        assertThat(ai.calls()).isEqualTo(1);
        mvc.perform(authorized(post("/api/ai/jobs"), token)
                        .content(json(Map.of("operation", "ANALYZE_ERROR", "error", "another error")))
                        .header("Idempotency-Key", "job-key-000001"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void validatesTheJobBeforeQueueingIt() throws Exception {
        mvc.perform(authorized(post("/api/ai/jobs"), token)
                        .content(json(Map.of("operation", "IMPROVE", "projectId", projectId, "fileIds", List.of(fileId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("IMPROVE requires goals"));
        mvc.perform(authorized(post("/api/ai/jobs"), token)
                        .content(json(Map.of("operation", "REVIEW", "projectId", projectId, "fileIds", List.of(999_999)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
        mvc.perform(authorized(get("/api/ai/jobs"), token))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        assertThat(ai.calls()).isZero();
    }

    @Test
    void aUserCannotSeeOrCreateJobsOnAnotherUsersResources() throws Exception {
        String jobId = read(mvc.perform(authorized(post("/api/ai/jobs"), token)
                .content(json(Map.of("operation", "REVIEW", "projectId", projectId, "fileIds", List.of(fileId)))))
                .andReturn(), "$.data.jobId");
        String intruder = registerUser();

        mvc.perform(authorized(get("/api/ai/jobs/{id}", jobId), intruder))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_RESOURCE"));
        mvc.perform(authorized(post("/api/ai/jobs"), intruder)
                        .content(json(Map.of("operation", "REVIEW", "projectId", projectId, "fileIds", List.of(fileId)))))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(get("/api/ai/jobs/{id}", "00000000-0000-0000-0000-000000000000"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
        awaitTerminal(jobId);
    }

    private String awaitTerminal(String jobId) {
        String[] status = new String[1];
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            status[0] = read(mvc.perform(authorized(get("/api/ai/jobs/{id}", jobId), token)).andReturn(),
                    "$.data.status");
            return "COMPLETED".equals(status[0]) || "FAILED".equals(status[0]);
        });
        return status[0];
    }
}
