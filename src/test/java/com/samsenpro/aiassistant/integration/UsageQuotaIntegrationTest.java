package com.samsenpro.aiassistant.integration;

import com.samsenpro.aiassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Control de coste: cuota diaria de tokens por usuario, configurable sin tocar código. */
@TestPropertySource(properties = {
        "ai.limits.daily.tokens-per-user=3000",
        "ai.analysis.max-output-tokens.EXPLAIN=1600"
})
class UsageQuotaIntegrationTest extends IntegrationTest {

    @Test
    void rejectsRequestsOnceTheDailyTokenQuotaWouldBeExceeded() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "quota");
        long fileId = uploadFile(token, projectId, "src/A.java", "class A {\n    int value;\n}\n");
        // Cada llamada simulada consume 1500 tokens y cada petición reserva su prompt + 1600 de salida:
        // tras la primera, la segunda ya no cabe en la cuota de 3000
        ai.byDefault(prompt -> new com.samsenpro.aiassistant.ai.provider.AIResponse(
                com.samsenpro.aiassistant.support.ScriptedResponses.EXPLAIN, "scripted", "m", 1200, 300, false, "STOP"));

        int accepted = 0;
        int status;
        do {
            status = mvc.perform(authorized(post("/api/ai/explain"), token).header("Cache-Control", "no-cache")
                            .content(json(Map.of("projectId", projectId, "fileIds", List.of(fileId)))))
                    .andReturn().getResponse().getStatus();
            if (status == 200) {
                accepted++;
            }
        } while (status == 200 && accepted < 10);

        assertThat(accepted).isEqualTo(1);
        mvc.perform(authorized(post("/api/ai/explain"), token).header("Cache-Control", "no-cache")
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(fileId)))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("AI_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.error.details.reason").value("DAILY_TOKEN_LIMIT"))
                .andExpect(jsonPath("$.error.message").value(containsString("1500 of 3000 tokens")));
        assertThat(ai.calls()).isEqualTo(1);

        mvc.perform(authorized(get("/api/usage"), token))
                .andExpect(jsonPath("$.data.quota.dailyTokenLimit").value(3000))
                .andExpect(jsonPath("$.data.quota.tokensRemainingToday").value(1500));
        // Otro usuario tiene su propia cuota
        String other = registerUser();
        long otherProject = createProject(other, "quota");
        long otherFile = uploadFile(other, otherProject, "src/A.java", "class A {}\n");
        mvc.perform(authorized(post("/api/ai/explain"), other)
                        .content(json(Map.of("projectId", otherProject, "fileIds", List.of(otherFile)))))
                .andExpect(status().isOk());
    }
}
