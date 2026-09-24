package com.samsenpro.aiassistant.ai.provider;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.dto.ReviewResult;
import com.samsenpro.aiassistant.ai.parser.InvalidAIResponseException;
import com.samsenpro.aiassistant.ai.parser.StructuredOutputParser;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.support.FakeAIProvider;
import com.samsenpro.aiassistant.usage.AIMetrics;
import com.samsenpro.aiassistant.usage.AIUsageService;
import com.samsenpro.aiassistant.usage.UsageRecord;
import com.samsenpro.aiassistant.usage.UsageStatus;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AIClientTest {

    private static final String VALID = """
            {"summary": "ok", "issues": []}
            """;
    private static final AICallContext CONTEXT = new AICallContext(7L, 3L, AIOperation.REVIEW);
    private static final AIPrompt PROMPT = new AIPrompt(AIOperation.REVIEW, "review", "1.0.0", "system", "user", 500);

    private final FakeAIProvider primary = new FakeAIProvider("primary");
    private final FakeAIProvider fallback = new FakeAIProvider("backup");
    private AIUsageService usageService;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        usageService = mock(AIUsageService.class);
        registry = new SimpleMeterRegistry();
    }

    @Test
    void returnsTheValidatedResultAndRecordsUsage() {
        primary.thenReturn(VALID);

        AIResult<ReviewResult> result = client(null).generateStructured(PROMPT, CONTEXT, ReviewResult.class);

        assertThat(result.value().summary()).isEqualTo("ok");
        assertThat(result.provider()).isEqualTo("primary");
        assertThat(result.inputTokens()).isEqualTo(100);
        UsageRecord usage = singleUsage();
        assertThat(usage.status()).isEqualTo(UsageStatus.SUCCESS);
        assertThat(usage.userId()).isEqualTo(7L);
        assertThat(usage.projectId()).isEqualTo(3L);
        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(50);
        assertThat(registry.get("ai.requests").tag("status", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("ai.tokens.used").tag("type", "input").counter().count()).isEqualTo(100);
    }

    @Test
    void asksAgainWhenTheFirstResponseIsInvalidAndCountsTheTokensOfBothAttempts() {
        primary.thenReturn("not json").thenReturn(VALID);

        AIResult<ReviewResult> result = client(null).generateStructured(PROMPT, CONTEXT, ReviewResult.class);

        assertThat(result.value().summary()).isEqualTo("ok");
        assertThat(result.inputTokens()).isEqualTo(200);
        assertThat(allUsage()).extracting(UsageRecord::status)
                .containsExactly(UsageStatus.INVALID_RESPONSE, UsageStatus.SUCCESS);
        assertThat(registry.get("ai.requests.failed").tag("error", "invalid_ai_response").counter().count()).isEqualTo(1);
    }

    @Test
    void failsWithInvalidAIResponseWhenEveryAttemptIsInvalid() {
        primary.thenReturn("{}").thenReturn("");

        assertThatThrownBy(() -> client(null).generateStructured(PROMPT, CONTEXT, ReviewResult.class))
                .isInstanceOf(InvalidAIResponseException.class)
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.INVALID_AI_RESPONSE);
        assertThat(primary.calls()).isEqualTo(2);
    }

    @Test
    void doesNotRetryATruncatedResponse() {
        primary.thenReturn(new com.samsenpro.aiassistant.ai.provider.AIResponse("{\"summary\": \"cut", "primary",
                "m", 10, 500, false, "LENGTH"));

        assertThatThrownBy(() -> client(null).generateStructured(PROMPT, CONTEXT, ReviewResult.class))
                .satisfies(ex -> assertThat(((InvalidAIResponseException) ex).reason())
                        .isEqualTo(InvalidAIResponseException.Reason.TRUNCATED_RESPONSE));
        assertThat(primary.calls()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheSecondaryProviderWhenThePrimaryTimesOut() {
        primary.thenFail(ErrorCode.AI_PROVIDER_TIMEOUT);
        fallback.thenReturn(VALID);

        AIResult<ReviewResult> result = client("backup").generateStructured(PROMPT, CONTEXT, ReviewResult.class);

        assertThat(result.provider()).isEqualTo("backup");
        assertThat(allUsage()).extracting(UsageRecord::provider, UsageRecord::status, UsageRecord::errorCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("primary", UsageStatus.FAILED, "AI_PROVIDER_TIMEOUT"),
                        org.assertj.core.groups.Tuple.tuple("backup", UsageStatus.SUCCESS, null));
    }

    @Test
    void propagatesTheLastProviderFailureWhenThereIsNoFallback() {
        primary.thenFail(ErrorCode.AI_RATE_LIMIT);

        assertThatThrownBy(() -> client(null).generateStructured(PROMPT, CONTEXT, ReviewResult.class))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.AI_RATE_LIMIT);
                    assertThat(((ApiException) ex).retryAfter()).isEqualTo(Duration.ofSeconds(20));
                });
        assertThat(registry.get("ai.requests.failed").tag("error", "ai_rate_limit").counter().count()).isEqualTo(1);
    }

    @Test
    void doesNotFallBackOnErrorsThatAnotherProviderWouldNotFix() {
        primary.thenFail(ErrorCode.CONTEXT_TOO_LARGE);

        assertThatThrownBy(() -> client("backup").generateStructured(PROMPT, CONTEXT, ReviewResult.class))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.CONTEXT_TOO_LARGE);
        assertThat(fallback.calls()).isZero();
    }

    @Test
    void treatsUnexpectedProviderExceptionsAsUnavailable() {
        primary.thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> client(null).generate(PROMPT, CONTEXT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }

    @Test
    void freeTextGenerationRejectsEmptyAnswers() {
        primary.thenReturn("  ");

        assertThatThrownBy(() -> client(null).generate(PROMPT, CONTEXT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.INVALID_AI_RESPONSE);
        assertThat(singleUsage().status()).isEqualTo(UsageStatus.INVALID_RESPONSE);
    }

    @Test
    void metricsOnlyUseLowCardinalityTags() {
        primary.thenReturn(VALID).thenFail(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        AIClient client = client(null);
        client.generateStructured(PROMPT, CONTEXT, ReviewResult.class);
        assertThatThrownBy(() -> client.generateStructured(PROMPT, CONTEXT, ReviewResult.class));

        List<String> tagKeys = registry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(io.micrometer.core.instrument.Tag::getKey)
                .distinct()
                .toList();
        assertThat(tagKeys).containsAnyOf("operation", "provider", "status")
                .doesNotContain("userId", "projectId", "conversationId", "user", "project");
    }

    @Test
    void failsFastWhenTheConfiguredProviderDoesNotExist() {
        assertThatThrownBy(() -> new AIClient(List.of(primary), properties("missing", null), parser(),
                usageService, new AIMetrics(registry)))
                .hasMessageContaining("Unknown AI provider 'missing'");
        assertThatThrownBy(() -> new AIClient(List.of(primary), properties("primary", "primary"), parser(),
                usageService, new AIMetrics(registry)))
                .hasMessageContaining("must be different");
    }

    private AIClient client(String fallbackName) {
        return new AIClient(List.of(primary, fallback), properties("primary", fallbackName), parser(), usageService,
                new AIMetrics(registry));
    }

    private static AIProviderProperties properties(String primary, String fallback) {
        return new AIProviderProperties(primary, fallback, Duration.ofSeconds(10), Duration.ofSeconds(1), 2, 2,
                new AIProviderProperties.OpenAi("openai", "gpt-test", 0.2, true));
    }

    private static StructuredOutputParser parser() {
        return new StructuredOutputParser(Validation.buildDefaultValidatorFactory().getValidator());
    }

    private UsageRecord singleUsage() {
        List<UsageRecord> all = allUsage();
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    private List<UsageRecord> allUsage() {
        ArgumentCaptor<UsageRecord> captor = ArgumentCaptor.forClass(UsageRecord.class);
        verify(usageService, org.mockito.Mockito.atLeast(0)).record(captor.capture());
        return captor.getAllValues();
    }
}
