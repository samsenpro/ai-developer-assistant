package com.samsenpro.aiassistant.integration;

import com.samsenpro.aiassistant.ai.provider.AIPrompt;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo de las operaciones de IA con el proveedor LLM simulado. */
class AIOperationsIntegrationTest extends IntegrationTest {

    private static final String SERVICE_PATH = "src/main/java/com/shop/UserService.java";
    private static final String USER_SERVICE = """
            package com.shop;

            public class UserService {
                public User findByEmail(String email) {
                    return jdbc.query("SELECT * FROM users WHERE email = '" + email + "'");
                }

                private final UserRepository userRepository;
                private final String apiKey = "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789";
            }
            """;
    private static final String USER_REPOSITORY = """
            package com.shop;

            public interface UserRepository {
                User findById(Long id);
            }
            """;

    private String token;
    private long projectId;
    private long serviceId;
    private long repositoryId;

    @BeforeEach
    void setUpProject() throws Exception {
        token = registerUser();
        projectId = createProject(token, "ecommerce-api");
        serviceId = uploadFile(token, projectId, SERVICE_PATH, USER_SERVICE);
        repositoryId = uploadFile(token, projectId, "src/main/java/com/shop/UserRepository.java", USER_REPOSITORY);
    }

    @Test
    void reviewFlowFromFilesToPersistedValidatedResult() throws Exception {
        MvcResult result = mvc.perform(authorized(post("/api/ai/review"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId), "language", "JAVA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.result.summary").isString())
                .andExpect(jsonPath("$.data.result.issues[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.result.issues[0].line").value(5))
                // La línea 9999 no existe en el archivo: nunca se devuelve una línea inventada
                .andExpect(jsonPath("$.data.result.issues[1].line").value(nullValue()))
                .andExpect(jsonPath("$.data.warnings", hasItem(containsString("masked"))))
                .andExpect(jsonPath("$.data.warnings", hasItem(containsString("line numbers outside the file"))))
                .andExpect(jsonPath("$.data.contextFiles[0].role").value("PRIMARY"))
                .andExpect(jsonPath("$.data.contextFiles[1].path").value("src/main/java/com/shop/UserRepository.java"))
                .andExpect(jsonPath("$.data.contextFiles[1].role").value("RELATED"))
                .andExpect(jsonPath("$.data.provider").value("scripted"))
                .andExpect(jsonPath("$.data.inputTokens").value(100))
                .andReturn();

        // El prompt: instrucciones en el mensaje de sistema; el código delimitado como datos y sin secretos
        AIPrompt prompt = ai.prompts().getFirst();
        assertThat(prompt.system()).contains("UNTRUSTED DATA").doesNotContain("class UserService");
        assertThat(prompt.user()).contains("<<<FILE id=ctx-").contains("role=PRIMARY").contains("role=RELATED")
                .contains("  5 |         return jdbc.query").doesNotContain("sk-proj-abcdefghijklmnop");

        int analysisId = read(result, "$.data.id");
        mvc.perform(authorized(get("/api/analyses/{id}", analysisId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.issues[0].category").value("SECURITY"));
        mvc.perform(authorized(get("/api/projects/{id}/analyses", projectId), token))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].operation").value("REVIEW"));
        mvc.perform(authorized(get("/api/usage"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.requests").value(1))
                .andExpect(jsonPath("$.data.totals.totalTokens").value(150))
                .andExpect(jsonPath("$.data.byOperation[0].operation").value("REVIEW"))
                .andExpect(jsonPath("$.data.quota.tokensUsedToday").value(150));
        mvc.perform(authorized(get("/api/usage/records"), token))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].provider").value("scripted"));
    }

    @Test
    void everyOperationReturnsItsStructuredResult() throws Exception {
        Map<String, Object> code = Map.of("projectId", projectId, "fileIds", List.of(serviceId));
        mvc.perform(authorized(post("/api/ai/explain"), token).content(json(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.purpose").value("Manage users"))
                .andExpect(jsonPath("$.data.result.dependencies[0].type").value("INTERNAL"));
        mvc.perform(authorized(post("/api/ai/improve"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId),
                                "goals", List.of("security", "clean-code")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.improvedCode[0].file").value(SERVICE_PATH))
                // El código mejorado se devuelve limpio (sin los números de línea del prompt)
                .andExpect(jsonPath("$.data.result.improvedCode[0].content").value("class UserService {}"));
        mvc.perform(authorized(post("/api/ai/generate-tests"), token).content(json(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.testFramework").value("JUnit 5"))
                .andExpect(jsonPath("$.data.result.detectedFramework").doesNotExist());
        mvc.perform(authorized(post("/api/ai/documentation"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId), "type", "JAVADOC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.documents[0].format").value("java"));
        mvc.perform(authorized(post("/api/ai/analyze-error"), token)
                        .content(json(Map.of("error", "java.lang.NullPointerException",
                                "stackTrace", "at com.shop.UserService.login(UserService.java:5)\npassword=\"hunter22hunter\""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").doesNotExist())
                .andExpect(jsonPath("$.data.result.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.data.warnings", hasItem(containsString("request text were masked"))));

        assertThat(ai.prompts()).extracting(AIPrompt::promptId)
                .containsExactly("explain", "improve", "tests", "documentation", "error-analysis");
        // La estrategia de tests se decide antes de llamar al modelo
        assertThat(ai.prompts().get(2).user()).contains("JUnit 5").contains("Mockito");
        assertThat(ai.prompts().get(4).user()).contains("<<<DATA id=").doesNotContain("hunter22hunter");
    }

    @Test
    void invalidResponsesAreRetriedOnceAndThenReportedWithoutCorruptData() throws Exception {
        ai.thenReturn("Sure! Here is my review: it looks fine.").thenReturn("{\"issues\": []}");

        MvcResult result = mvc.perform(authorized(post("/api/ai/review"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId)))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_AI_RESPONSE"))
                .andExpect(jsonPath("$.error.details.reason").value("SCHEMA_VIOLATION"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("looks fine");
        assertThat(ai.calls()).isEqualTo(2);

        mvc.perform(authorized(get("/api/projects/{id}/analyses", projectId), token))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].errorCode").value("INVALID_AI_RESPONSE"));
        mvc.perform(authorized(get("/api/usage/records"), token))
                .andExpect(jsonPath("$.data.content[0].status").value("INVALID_RESPONSE"))
                .andExpect(jsonPath("$.data.content[1].status").value("INVALID_RESPONSE"));
    }

    @Test
    void recoversWhenTheSecondAttemptIsValid() throws Exception {
        ai.thenReturn("{ not json");

        mvc.perform(authorized(post("/api/ai/explain"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.inputTokens").value(200));
    }

    @Test
    void providerFailuresAreMappedToStableErrorCodes() throws Exception {
        String body = json(Map.of("projectId", projectId, "fileIds", List.of(serviceId)));

        ai.thenFail(ErrorCode.AI_PROVIDER_TIMEOUT);
        mvc.perform(authorized(post("/api/ai/review"), token).content(body).header("Cache-Control", "no-cache"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("AI_PROVIDER_TIMEOUT"))
                .andExpect(jsonPath("$.error.message").value("The AI provider did not respond in time"));

        ai.thenFail(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        mvc.perform(authorized(post("/api/ai/review"), token).content(body).header("Cache-Control", "no-cache"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_PROVIDER_UNAVAILABLE"));

        ai.thenFail(ErrorCode.AI_RATE_LIMIT);
        mvc.perform(authorized(post("/api/ai/review"), token).content(body).header("Cache-Control", "no-cache"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "20"))
                .andExpect(jsonPath("$.error.code").value("AI_RATE_LIMIT"))
                .andExpect(jsonPath("$.error.details.source").value("PROVIDER"));

        ai.thenReturn("").thenReturn("   ");
        mvc.perform(authorized(post("/api/ai/review"), token).content(body).header("Cache-Control", "no-cache"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.details.reason").value("EMPTY_RESPONSE"));

        mvc.perform(authorized(get("/api/usage"), token))
                .andExpect(jsonPath("$.data.totals.failedRequests").value(5));
    }

    @Test
    void theSameIdempotencyKeyNeverCallsTheLlmTwice() throws Exception {
        String body = json(Map.of("projectId", projectId, "fileIds", List.of(serviceId)));

        MvcResult first = mvc.perform(authorized(post("/api/ai/review"), token).content(body)
                        .header("Idempotency-Key", "review-2026-0001").header("Cache-Control", "no-cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andReturn();
        mvc.perform(authorized(post("/api/ai/review"), token).content(body)
                        .header("Idempotency-Key", "review-2026-0001").header("Cache-Control", "no-cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.id").value((Integer) read(first, "$.data.id")));
        assertThat(ai.calls()).isEqualTo(1);

        mvc.perform(authorized(post("/api/ai/review"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(repositoryId))))
                        .header("Idempotency-Key", "review-2026-0001"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(authorized(post("/api/ai/review"), token).content(body).header("Idempotency-Key", "bad key!"))
                .andExpect(status().isBadRequest());
        assertThat(ai.calls()).isEqualTo(1);
    }

    @Test
    void identicalRequestsReuseTheCachedResultUntilTheFileChanges() throws Exception {
        String body = json(Map.of("projectId", projectId, "fileIds", List.of(serviceId)));

        mvc.perform(authorized(post("/api/ai/explain"), token).content(body))
                .andExpect(jsonPath("$.data.cached").value(false));
        mvc.perform(authorized(post("/api/ai/explain"), token).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.inputTokens").value(0))
                .andExpect(jsonPath("$.data.result.purpose").value("Manage users"));
        assertThat(ai.calls()).isEqualTo(1);

        // Cache-Control: no-cache fuerza un análisis nuevo
        mvc.perform(authorized(post("/api/ai/explain"), token).content(body).header("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.data.cached").value(false));
        assertThat(ai.calls()).isEqualTo(2);

        // Otra operación sobre el mismo archivo no reutiliza el resultado
        mvc.perform(authorized(post("/api/ai/review"), token).content(body))
                .andExpect(jsonPath("$.data.cached").value(false));
        assertThat(ai.calls()).isEqualTo(3);

        // Si el archivo cambia, cambia el hash de entrada y se vuelve a llamar al LLM
        mvc.perform(authorized(put("/api/projects/{p}/files/{f}", projectId, serviceId), token)
                        .content(json(Map.of("path", SERVICE_PATH, "content", USER_SERVICE + "// changed\n"))))
                .andExpect(status().isOk());
        mvc.perform(authorized(post("/api/ai/explain"), token).content(body))
                .andExpect(jsonPath("$.data.cached").value(false));
        assertThat(ai.calls()).isEqualTo(4);

        // Otro usuario con el mismo código nunca recibe el resultado del primero
        String other = registerUser();
        long otherProject = createProject(other, "copy");
        long otherFile = uploadFile(other, otherProject, SERVICE_PATH, USER_SERVICE);
        mvc.perform(authorized(post("/api/ai/explain"), other)
                        .content(json(Map.of("projectId", otherProject, "fileIds", List.of(otherFile)))))
                .andExpect(jsonPath("$.data.cached").value(false));
        assertThat(ai.calls()).isEqualTo(5);
    }

    @Test
    void enforcesThePerUserRateLimitWithAClearMessage() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(authorized(post("/api/ai/documentation"), token).header("Cache-Control", "no-cache")
                            .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId), "type", "README"))))
                    .andExpect(status().isOk());
        }
        mvc.perform(authorized(post("/api/ai/documentation"), token).header("Cache-Control", "no-cache")
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId), "type", "README"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("AI_RATE_LIMIT"))
                .andExpect(jsonPath("$.error.message").value(containsString("at most 2 requests every 1h")))
                .andExpect(jsonPath("$.error.details.reason").value("USER_RATE_LIMIT"));
        assertThat(ai.calls()).isEqualTo(2);
    }

    @Test
    void aUserCannotRunAIOperationsOnAnotherUsersCode() throws Exception {
        String intruder = registerUser();
        long intruderProject = createProject(intruder, "mine");

        mvc.perform(authorized(post("/api/ai/review"), intruder)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_RESOURCE"));
        // Tampoco usando un proyecto propio con el ID de un archivo ajeno
        mvc.perform(authorized(post("/api/ai/explain"), intruder)
                        .content(json(Map.of("projectId", intruderProject, "fileIds", List.of(serviceId)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
        assertThat(ai.calls()).isZero();

        MvcResult own = mvc.perform(authorized(post("/api/ai/review"), token)
                .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId))))).andReturn();
        mvc.perform(authorized(get("/api/analyses/{id}", (Integer) read(own, "$.data.id")), intruder))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesOperationInputsBeforeCallingTheLlm() throws Exception {
        long sqlFile = uploadFile(token, projectId, "db/schema.sql", "CREATE TABLE users (id INT);");

        mvc.perform(authorized(post("/api/ai/generate-tests"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(sqlFile)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(containsString("Java, Python, JavaScript and TypeScript")));
        mvc.perform(authorized(post("/api/ai/review"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of()))))
                .andExpect(status().isBadRequest());
        mvc.perform(authorized(post("/api/ai/improve"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId), "goals", List.of("magic")))))
                .andExpect(status().isBadRequest());
        mvc.perform(authorized(post("/api/ai/review"), token)
                        .content(json(Map.of("projectId", projectId, "fileIds", List.of(serviceId), "language", "PYTHON"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(containsString("PYTHON")));
        mvc.perform(authorized(post("/api/ai/review"), token)
                        .content(json(Map.of("projectId", projectId,
                                "fileIds", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.maxFilesPerRequest").value(10));
        assertThat(ai.calls()).isZero();
    }
}
