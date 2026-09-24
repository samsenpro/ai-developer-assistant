package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIUsageServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T22:00:00Z");
    private static final Instant START_OF_DAY = Instant.parse("2026-09-23T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private AIUsageRepository repository;
    private SimpleMeterRegistry registry;
    private AIUsageService service;

    @BeforeEach
    void setUp() {
        repository = mock(AIUsageRepository.class);
        registry = new SimpleMeterRegistry();
        UsageLimitsProperties limits = new UsageLimitsProperties(
                new UsageLimitsProperties.Rate(new UsageLimitsProperties.RateLimit(10, Duration.ofMinutes(1)),
                        Map.of(AIOperation.REVIEW, new UsageLimitsProperties.RateLimit(2, Duration.ofMinutes(1)))),
                new UsageLimitsProperties.Daily(10_000, 50, Map.of(AIOperation.GENERATE_TESTS, 3L)),
                5);
        service = new AIUsageService(repository, new AIRateLimiter(limits), limits, new AIMetrics(registry), clock);
    }

    @Test
    void recordsEveryCallWithItsTokens() {
        service.record(new UsageRecord(1L, 2L, AIOperation.REVIEW, "openai", "gpt", 300, 100, false, 1200,
                UsageStatus.SUCCESS, null));

        ArgumentCaptor<AIUsage> saved = ArgumentCaptor.forClass(AIUsage.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTotalTokens()).isEqualTo(400);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void failingToRecordUsageNeverBreaksTheOperation() {
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service.record(new UsageRecord(1L, null, AIOperation.CHAT, "openai", "gpt", 1, 1, false,
                1, UsageStatus.SUCCESS, null))).doesNotThrowAnyException();
    }

    @Test
    void rateLimitsBurstsPerUserAndOperationWithRetryAfter() {
        service.checkRateLimit(1L, AIOperation.REVIEW);
        service.checkRateLimit(1L, AIOperation.REVIEW);

        assertThatThrownBy(() -> service.checkRateLimit(1L, AIOperation.REVIEW))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.code()).isEqualTo(ErrorCode.AI_RATE_LIMIT);
                    assertThat(api.getMessage()).contains("at most 2 requests every 1m");
                    assertThat(api.details()).containsEntry("reason", "USER_RATE_LIMIT").containsEntry("limit", 2L);
                    assertThat(api.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
                });
        // Otro usuario y otra operación tienen su propio cupo
        assertThatCode(() -> service.checkRateLimit(2L, AIOperation.REVIEW)).doesNotThrowAnyException();
        assertThatCode(() -> service.checkRateLimit(1L, AIOperation.EXPLAIN)).doesNotThrowAnyException();
        assertThat(registry.get("ai.rate.limited").tag("reason", "user_rate_limit").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsRequestsThatWouldExceedTheDailyTokenQuota() {
        when(repository.sumTokensSince(1L, START_OF_DAY)).thenReturn(9_500L);

        assertThatThrownBy(() -> service.checkQuota(1L, AIOperation.REVIEW, 800))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.code()).isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED);
                    assertThat(api.getMessage()).contains("9500 of 10000 tokens").contains("about 800");
                    assertThat(api.details()).containsEntry("reason", "DAILY_TOKEN_LIMIT")
                            .containsEntry("resetsAt", "2026-09-24T00:00:00Z");
                    assertThat(api.retryAfter()).isEqualTo(Duration.ofHours(2));
                });
        assertThatCode(() -> service.checkQuota(1L, AIOperation.REVIEW, 400)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsAboveTheDailyRequestLimits() {
        when(repository.countByUserIdAndCreatedAtGreaterThanEqual(1L, START_OF_DAY)).thenReturn(50L);
        assertThatThrownBy(() -> service.checkQuota(1L, AIOperation.EXPLAIN, 10))
                .satisfies(ex -> assertThat(((ApiException) ex).details()).containsEntry("reason", "DAILY_REQUEST_LIMIT"));

        when(repository.countByUserIdAndCreatedAtGreaterThanEqual(1L, START_OF_DAY)).thenReturn(5L);
        when(repository.countByUserIdAndOperationAndCreatedAtGreaterThanEqual(eq(1L), eq(AIOperation.GENERATE_TESTS),
                eq(START_OF_DAY))).thenReturn(3L);
        assertThatThrownBy(() -> service.checkQuota(1L, AIOperation.GENERATE_TESTS, 10))
                .satisfies(ex -> assertThat(((ApiException) ex).details())
                        .containsEntry("reason", "DAILY_OPERATION_LIMIT")
                        .containsEntry("operation", "GENERATE_TESTS"));
    }
}
