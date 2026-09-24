package com.samsenpro.aiassistant.integration;

import com.samsenpro.aiassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndProjectsIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginAndAccessProtectedRoutes() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "email", "Alice@Example.com", "password", "Str0ngPass1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.timestamp").exists());

        String token = read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", "Str0ngPass1"))))
                .andExpect(status().isOk()).andReturn(), "$.data.accessToken");

        mvc.perform(authorized(get("/api/projects"), token)).andExpect(status().isOk());
    }

    @Test
    void rejectsBadCredentialsDuplicatesAndMissingTokensWithTheCommonErrorFormat() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "bob", "email", "bob@example.com", "password", "Str0ngPass1"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "bob", "email", "bob2@example.com", "password", "Str0ngPass1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "bob", "password", "wrong-pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/projects").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesRequestsAndNeverReturnsStackTraces() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "x", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.details.fieldErrors[*].field",
                        org.hamcrest.Matchers.hasItems("username", "email", "password")));

        String token = registerUser();
        mvc.perform(authorized(post("/api/projects"), token).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(content -> org.assertj.core.api.Assertions.assertThat(
                        content.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("at com."));
        mvc.perform(authorized(get("/api/does-not-exist"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    void projectCrud() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "my-ecommerce");

        mvc.perform(authorized(get("/api/projects/{id}", projectId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("my-ecommerce"))
                .andExpect(jsonPath("$.data.language").value("JAVA"));
        mvc.perform(authorized(put("/api/projects/{id}", projectId), token)
                        .content(json(Map.of("name", "shop-api", "language", "JAVA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("shop-api"));
        mvc.perform(authorized(post("/api/projects"), token)
                        .content(json(Map.of("name", "shop-api", "language", "JAVA"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NAME_TAKEN"));
        mvc.perform(authorized(get("/api/projects"), token))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(authorized(delete("/api/projects/{id}", projectId), token)).andExpect(status().isNoContent());
        mvc.perform(authorized(get("/api/projects/{id}", projectId), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void filesAreValidatedAndListedWithoutContent() throws Exception {
        String token = registerUser();
        long projectId = createProject(token, "files");

        long fileId = uploadFile(token, projectId, "src/UserService.java", "class UserService {}\n");
        mvc.perform(multipart("/api/projects/{id}/files", projectId)
                        .file(new MockMultipartFile("file", "OrderService.java", "text/plain",
                                "class OrderService {}\n".getBytes()))
                        .param("path", "src/OrderService.java")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.language").value("JAVA"));

        mvc.perform(authorized(get("/api/projects/{id}/files", projectId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].content").doesNotExist());
        mvc.perform(authorized(get("/api/projects/{p}/files/{f}", projectId, fileId), token))
                .andExpect(jsonPath("$.data.content").value("class UserService {}\n"))
                .andExpect(jsonPath("$.data.contentHash").isString());

        mvc.perform(authorized(post("/api/projects/{id}/files", projectId), token)
                        .content(json(Map.of("path", "script.rb", "content", "puts 1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(containsString(".java")));
        mvc.perform(authorized(post("/api/projects/{id}/files", projectId), token)
                        .content(json(Map.of("path", "../../etc/passwd.py", "content", "x"))))
                .andExpect(status().isBadRequest());
        mvc.perform(authorized(post("/api/projects/{id}/files", projectId), token)
                        .content(json(Map.of("path", "src/UserService.java", "content", "class X {}"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FILE_PATH_TAKEN"));
        mvc.perform(authorized(post("/api/projects/{id}/files", projectId), token)
                        .content(json(Map.of("path", "src/Big.java", "content", "a".repeat(110_000)))))
                .andExpect(status().isPayloadTooLarge());

        mvc.perform(authorized(put("/api/projects/{p}/files/{f}", projectId, fileId), token)
                        .content(json(Map.of("path", "src/UserService.java", "content", "class UserService { int a; }"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(containsString("int a")));
        mvc.perform(authorized(delete("/api/projects/{p}/files/{f}", projectId, fileId), token))
                .andExpect(status().isNoContent());
        mvc.perform(authorized(get("/api/projects/{p}/files/{f}", projectId, fileId), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void aUserCannotAccessAnotherUsersProjectsOrCode() throws Exception {
        String owner = registerUser();
        String intruder = registerUser();
        long projectId = createProject(owner, "private");
        long fileId = uploadFile(owner, projectId, "src/Secret.java", "class Secret { String code = \"x\"; }");
        long intruderProject = createProject(intruder, "mine");

        mvc.perform(authorized(get("/api/projects/{id}", projectId), intruder))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_RESOURCE"))
                .andExpect(content -> org.assertj.core.api.Assertions.assertThat(
                        content.getResponse().getContentAsString()).doesNotContain("private"));
        mvc.perform(authorized(get("/api/projects/{p}/files/{f}", projectId, fileId), intruder))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(authorized(get("/api/projects/{id}/files", projectId), intruder))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(put("/api/projects/{id}", projectId), intruder)
                        .content(json(Map.of("name", "hacked", "language", "JAVA"))))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(delete("/api/projects/{p}/files/{f}", projectId, fileId), intruder))
                .andExpect(status().isForbidden());
        // El archivo ajeno tampoco es accesible a través de un proyecto propio
        mvc.perform(authorized(get("/api/projects/{p}/files/{f}", intruderProject, fileId), intruder))
                .andExpect(status().isNotFound());
        mvc.perform(authorized(get("/api/projects"), intruder))
                .andExpect(jsonPath("$.data.content[*].name").value(not(containsString("private"))));
        mvc.perform(authorized(get("/api/projects/{p}/files/{f}", projectId, fileId), owner))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"));
    }
}
