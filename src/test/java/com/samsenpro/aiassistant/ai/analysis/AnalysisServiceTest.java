package com.samsenpro.aiassistant.ai.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.dto.ReviewResult;
import com.samsenpro.aiassistant.ai.analysis.handler.ReviewHandler;
import com.samsenpro.aiassistant.ai.context.CodeOutline;
import com.samsenpro.aiassistant.ai.context.ContextBuilder;
import com.samsenpro.aiassistant.ai.context.ContextProperties;
import com.samsenpro.aiassistant.ai.context.PromptInjectionDetector;
import com.samsenpro.aiassistant.ai.context.RelatedFileFinder;
import com.samsenpro.aiassistant.ai.context.SecretRedactor;
import com.samsenpro.aiassistant.ai.context.TokenEstimator;
import com.samsenpro.aiassistant.ai.context.UntrustedContentRenderer;
import com.samsenpro.aiassistant.ai.parser.StructuredOutputParser;
import com.samsenpro.aiassistant.ai.prompt.PromptBuilder;
import com.samsenpro.aiassistant.ai.prompt.PromptService;
import com.samsenpro.aiassistant.ai.provider.AIClient;
import com.samsenpro.aiassistant.ai.provider.AIProviderProperties;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.file.SourceFileService;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import com.samsenpro.aiassistant.project.Project;
import com.samsenpro.aiassistant.project.ProjectService;
import com.samsenpro.aiassistant.support.FakeAIProvider;
import com.samsenpro.aiassistant.usage.AIMetrics;
import com.samsenpro.aiassistant.usage.AIUsageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.samsenpro.aiassistant.support.TestFiles.USER_SERVICE;
import static com.samsenpro.aiassistant.support.TestFiles.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Idempotencia, caché y control de coste del flujo de análisis, con el LLM simulado. */
class AnalysisServiceTest {

    private static final long USER_ID = 7L;
    private static final long PROJECT_ID = 1L;
    private static final String KEY = "review-key-0001";
    private static final String VALID_REVIEW = "{\"summary\": \"ok\", \"issues\": []}";

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final FakeAIProvider provider = new FakeAIProvider("fake");
    private final AIOperationCommand command = AIOperationCommand.code(AIOperation.REVIEW,
            new CodeOperationRequest(PROJECT_ID, List.of(10L), null, null, false));

    private SourceFileService fileService;
    private AIUsageService usageService;
    private AnalysisResultRepository repository;
    private AnalysisCache cache;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        ProjectService projectService = mock(ProjectService.class);
        when(projectService.requireOwned(USER_ID, PROJECT_ID))
                .thenReturn(new Project(USER_ID, "shop", null, ProgrammingLanguage.JAVA, clock.instant()));
        fileService = mock(SourceFileService.class);
        givenFileContent(USER_SERVICE);
        usageService = mock(AIUsageService.class);
        repository = mock(AnalysisResultRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByUserIdAndIdempotencyKey(anyLong(), anyString())).thenReturn(Optional.empty());
        cache = mock(AnalysisCache.class);

        ContextProperties contextProperties = new ContextProperties(12_000, 16_000, 3.5, 10, 5, 2, 4, true);
        TokenEstimator estimator = new TokenEstimator(contextProperties);
        UntrustedContentRenderer renderer = new UntrustedContentRenderer();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AIClient aiClient = new AIClient(List.of(provider),
                new AIProviderProperties("fake", null, Duration.ofSeconds(5), Duration.ofSeconds(1), 1, 1,
                        new AIProviderProperties.OpenAi("openai", "m", 0.2, true)),
                new StructuredOutputParser(Validation.buildDefaultValidatorFactory().getValidator()),
                usageService, new AIMetrics(registry));
        service = new AnalysisService(List.of(new ReviewHandler()), projectService, fileService,
                new ContextBuilder(fileService, new RelatedFileFinder(), new CodeOutline(), new SecretRedactor(),
                        new PromptInjectionDetector(), renderer, estimator, contextProperties),
                new PromptBuilder(new PromptService(), estimator, contextProperties), renderer, new SecretRedactor(),
                aiClient, usageService, repository, cache, new AIMetrics(registry), contextProperties,
                new AnalysisProperties(Duration.ofDays(7), 1000, Map.of()), objectMapper, clock);
    }

    @Test
    void completesTheAnalysisAndPersistsTheResult() {
        provider.thenReturn(VALID_REVIEW);

        AnalysisView<ReviewResult> view = service.execute(USER_ID, command,
                AnalysisService.ExecutionOptions.sync(null, true), ReviewResult.class);

        assertThat(view.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(view.result().summary()).isEqualTo("ok");
        assertThat(view.provider()).isEqualTo("fake");
        assertThat(view.inputTokens()).isEqualTo(100);
        verify(usageService).checkRateLimit(USER_ID, AIOperation.REVIEW);
        verify(usageService).checkQuota(eq(USER_ID), eq(AIOperation.REVIEW), anyInt());
        verify(cache).put(eq(USER_ID), anyString(), any());
    }

    @Test
    void replaysACompletedRequestWithTheSameIdempotencyKeyWithoutCallingTheLlm() {
        AnalysisResult stored = AnalysisResult.processing(USER_ID, PROJECT_ID, AIOperation.REVIEW, List.of(10L),
                objectMapper.createArrayNode(), "hash", "1.0.0", KEY, service.fingerprint(command), clock.instant());
        stored.complete(objectMapper.createObjectNode().put("summary", "stored").set("issues",
                objectMapper.createArrayNode()), List.of(), "fake", "m", 10, 5, clock.instant());
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(stored));

        AnalysisView<ReviewResult> view = service.execute(USER_ID, command,
                AnalysisService.ExecutionOptions.sync(KEY, true), ReviewResult.class);

        assertThat(view.replayed()).isTrue();
        assertThat(view.result().summary()).isEqualTo("stored");
        assertThat(provider.calls()).isZero();
        verify(usageService, never()).checkRateLimit(anyLong(), any());
    }

    @Test
    void rejectsAnIdempotencyKeyReusedWithADifferentRequest() {
        AnalysisResult stored = AnalysisResult.processing(USER_ID, PROJECT_ID, AIOperation.REVIEW, List.of(99L),
                objectMapper.createArrayNode(), "hash", "1.0.0", KEY, "another-fingerprint", clock.instant());
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(stored));

        assertError(() -> service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(KEY, true),
                ReviewResult.class), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(provider.calls()).isZero();
    }

    @Test
    void rejectsARequestWhileTheSameKeyIsStillProcessing() {
        AnalysisResult inProgress = AnalysisResult.processing(USER_ID, PROJECT_ID, AIOperation.REVIEW, List.of(10L),
                objectMapper.createArrayNode(), "hash", "1.0.0", KEY, service.fingerprint(command), clock.instant());
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(inProgress));

        assertError(() -> service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(KEY, true),
                ReviewResult.class), ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        assertThat(provider.calls()).isZero();
    }

    @Test
    void retriesAFailedRequestWithTheSameKeyReusingItsRow() {
        AnalysisResult failed = AnalysisResult.processing(USER_ID, PROJECT_ID, AIOperation.REVIEW, List.of(10L),
                objectMapper.createArrayNode(), "hash", "1.0.0", KEY, service.fingerprint(command), clock.instant());
        failed.fail("AI_PROVIDER_TIMEOUT", "timeout", clock.instant());
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(failed));
        provider.thenReturn(VALID_REVIEW);

        AnalysisView<ReviewResult> view = service.execute(USER_ID, command,
                AnalysisService.ExecutionOptions.sync(KEY, true), ReviewResult.class);

        assertThat(view.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(view.error()).isNull();
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void aConcurrentRequestThatClaimsTheSameKeyFirstWins() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertError(() -> service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(KEY, true),
                ReviewResult.class), ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        assertThat(provider.calls()).isZero();
    }

    @Test
    void reusesACachedResultForIdenticalInputWithoutCallingTheLlmOrSpendingRateLimit() {
        when(cache.find(eq(USER_ID), anyString())).thenReturn(new AnalysisCache.CachedAnalysis(5L, USER_ID, PROJECT_ID,
                AIOperation.REVIEW, "hash", "1.0.0",
                objectMapper.createObjectNode().put("summary", "cached").set("issues", objectMapper.createArrayNode()),
                "fake", "m"));

        AnalysisView<ReviewResult> view = service.execute(USER_ID, command,
                AnalysisService.ExecutionOptions.sync(null, true), ReviewResult.class);

        assertThat(view.cached()).isTrue();
        assertThat(view.result().summary()).isEqualTo("cached");
        assertThat(view.inputTokens()).isZero();
        assertThat(provider.calls()).isZero();
        verify(usageService, never()).checkRateLimit(anyLong(), any());
        verify(usageService, never()).checkQuota(anyLong(), any(), anyInt());
    }

    @Test
    void cacheCanBeBypassed() {
        provider.thenReturn(VALID_REVIEW);

        service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(null, false), ReviewResult.class);

        verify(cache, never()).find(anyLong(), anyString());
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void theInputHashChangesWhenTheFileChanges() {
        provider.thenReturn(VALID_REVIEW).thenReturn(VALID_REVIEW).thenReturn(VALID_REVIEW);
        service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(null, true), ReviewResult.class);
        service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(null, true), ReviewResult.class);
        givenFileContent(USER_SERVICE.replace("orElseThrow()", "orElse(null)"));
        service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(null, true), ReviewResult.class);

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(cache, atLeastOnce()).find(eq(USER_ID), hashes.capture());
        assertThat(hashes.getAllValues()).hasSize(3);
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1));
        assertThat(hashes.getAllValues().get(2)).isNotEqualTo(hashes.getAllValues().get(0));
    }

    @Test
    void doesNotCallTheLlmOrClaimTheKeyWhenTheQuotaIsExceeded() {
        doThrow(new ApiException(ErrorCode.AI_QUOTA_EXCEEDED, "quota")).when(usageService)
                .checkQuota(eq(USER_ID), eq(AIOperation.REVIEW), anyInt());

        assertError(() -> service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(KEY, true),
                ReviewResult.class), ErrorCode.AI_QUOTA_EXCEEDED);
        assertThat(provider.calls()).isZero();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void persistsTheFailureWhenTheProviderFails() {
        provider.thenFail(ErrorCode.AI_PROVIDER_TIMEOUT);

        assertError(() -> service.execute(USER_ID, command, AnalysisService.ExecutionOptions.sync(null, true),
                ReviewResult.class), ErrorCode.AI_PROVIDER_TIMEOUT);

        ArgumentCaptor<AnalysisResult> saved = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(saved.getValue().getErrorCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(saved.getValue().getResult()).isNull();
    }

    @Test
    void sendsTheCodeOnlyInsideUntrustedDataBlocksAndWithSecretsMasked() {
        givenFileContent(USER_SERVICE.replace("public User find(Long id) {",
                "private String token = \"ghp_abcdefghijklmnopqrstuvwxyz0123456789AB\";\n    public User find(Long id) {"));
        provider.thenReturn(VALID_REVIEW);

        AnalysisView<ReviewResult> view = service.execute(USER_ID, command,
                AnalysisService.ExecutionOptions.sync(null, true), ReviewResult.class);

        var prompt = provider.prompts().getFirst();
        assertThat(prompt.system()).doesNotContain("class UserService");
        assertThat(prompt.user()).contains("<<<FILE id=ctx-").contains("class UserService")
                .doesNotContain("ghp_abcdefghijklmnopqrstuvwxyz");
        assertThat(view.warnings()).anyMatch(warning -> warning.contains("masked"));
    }

    private void givenFileContent(String content) {
        FileSnapshot file = snapshot(10, "src/UserService.java", content);
        when(fileService.loadSelected(eq(PROJECT_ID), any())).thenReturn(List.of(file));
        when(fileService.index(PROJECT_ID)).thenReturn(List.of());
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(code));
    }
}
