package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.web.PageRequests;
import com.samsenpro.aiassistant.common.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Control de consumo y coste de la IA:
 * <ul>
 *     <li>registro de cada llamada al proveedor (tokens, duración, resultado);</li>
 *     <li>rate limiting por usuario y operación (ráfagas);</li>
 *     <li>cuotas diarias de tokens y de peticiones, por usuario y por operación.</li>
 * </ul>
 * Cuando se supera un límite se responde 429 explicando qué límite fue, su valor y cuándo se
 * puede volver a intentar.
 */
@Service
public class AIUsageService {

    private static final Logger log = LoggerFactory.getLogger(AIUsageService.class);

    private final AIUsageRepository repository;
    private final AIRateLimiter rateLimiter;
    private final UsageLimitsProperties limits;
    private final AIMetrics metrics;
    private final Clock clock;

    public AIUsageService(AIUsageRepository repository, AIRateLimiter rateLimiter, UsageLimitsProperties limits,
                          AIMetrics metrics, Clock clock) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.limits = limits;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Registra una llamada al proveedor. Un fallo al registrar nunca rompe la operación del usuario. */
    public void record(UsageRecord record) {
        try {
            repository.save(new AIUsage(record, clock.instant()));
        } catch (RuntimeException ex) {
            log.error("Could not record AI usage for operation {}", record.operation(), ex);
        }
    }

    public void checkRateLimit(Long userId, AIOperation operation) {
        rateLimiter.tryConsume(userId, operation).ifPresent(retryAfter -> {
            UsageLimitsProperties.RateLimit limit = rateLimiter.limitFor(operation);
            metrics.recordRateLimited(operation, "USER_RATE_LIMIT");
            throw new ApiException(ErrorCode.AI_RATE_LIMIT,
                    "Rate limit exceeded for %s: at most %d requests every %s. Retry in %d seconds."
                            .formatted(operation, limit.requests(), format(limit.period()), seconds(retryAfter)),
                    details("USER_RATE_LIMIT", operation, limit.requests(), null, null, retryAfter),
                    retryAfter, null);
        });
    }

    /**
     * Comprueba las cuotas diarias antes de llamar al proveedor.
     *
     * @param estimatedTokens estimación de tokens de la petición (entrada + salida máxima): si no
     *                        caben en lo que queda de cuota, se rechaza antes de gastar nada
     */
    @Transactional(readOnly = true)
    public void checkQuota(Long userId, AIOperation operation, int estimatedTokens) {
        Instant dayStart = startOfDay();
        Duration untilReset = Duration.between(clock.instant(), dayStart.plus(1, ChronoUnit.DAYS));
        UsageLimitsProperties.Daily daily = limits.daily();

        long requestsToday = repository.countByUserIdAndCreatedAtGreaterThanEqual(userId, dayStart);
        if (requestsToday >= daily.requestsPerUser()) {
            throw quotaExceeded("DAILY_REQUEST_LIMIT", operation,
                    "Daily AI request limit reached (%d requests). The quota resets at 00:00 UTC."
                            .formatted(daily.requestsPerUser()),
                    daily.requestsPerUser(), requestsToday, untilReset);
        }

        Long operationLimit = daily.forOperation(operation);
        if (operationLimit != null) {
            long operationToday = repository.countByUserIdAndOperationAndCreatedAtGreaterThanEqual(userId, operation,
                    dayStart);
            if (operationToday >= operationLimit) {
                throw quotaExceeded("DAILY_OPERATION_LIMIT", operation,
                        "Daily limit for %s reached (%d requests). The quota resets at 00:00 UTC."
                                .formatted(operation, operationLimit),
                        operationLimit, operationToday, untilReset);
            }
        }

        long tokensToday = repository.sumTokensSince(userId, dayStart);
        if (tokensToday + estimatedTokens > daily.tokensPerUser()) {
            throw quotaExceeded("DAILY_TOKEN_LIMIT", operation,
                    "Daily AI token quota exceeded: %d of %d tokens used and this request needs about %d. The quota resets at 00:00 UTC."
                            .formatted(tokensToday, daily.tokensPerUser(), estimatedTokens),
                    daily.tokensPerUser(), tokensToday, untilReset);
        }
    }

    @Transactional(readOnly = true)
    public UsageSummary summary(Long userId, Instant from, Instant to) {
        List<OperationUsage> byOperation = repository.summarizeByOperation(userId, from, to).stream()
                .map(OperationUsage.Row::toUsage)
                .toList();
        long requests = byOperation.stream().mapToLong(OperationUsage::requests).sum();
        long failed = byOperation.stream().mapToLong(OperationUsage::failedRequests).sum();
        long input = byOperation.stream().mapToLong(OperationUsage::inputTokens).sum();
        long output = byOperation.stream().mapToLong(OperationUsage::outputTokens).sum();

        Instant dayStart = startOfDay();
        UsageLimitsProperties.Daily daily = limits.daily();
        long tokensToday = repository.sumTokensSince(userId, dayStart);
        long requestsToday = repository.countByUserIdAndCreatedAtGreaterThanEqual(userId, dayStart);
        UsageSummary.Quota quota = new UsageSummary.Quota(daily.tokensPerUser(), tokensToday,
                Math.max(0, daily.tokensPerUser() - tokensToday), daily.requestsPerUser(), requestsToday,
                Math.max(0, daily.requestsPerUser() - requestsToday), dayStart.plus(1, ChronoUnit.DAYS));

        return new UsageSummary(from, to, new UsageSummary.Totals(requests, failed, input, output, input + output),
                byOperation, quota);
    }

    @Transactional(readOnly = true)
    public PageResponse<UsageRecordView> records(Long userId, int page, int size) {
        return PageResponse.of(repository.findByUserId(userId,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), UsageRecordView::from);
    }

    private Instant startOfDay() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private ApiException quotaExceeded(String reason, AIOperation operation, String message, long limit, long used,
                                       Duration retryAfter) {
        metrics.recordRateLimited(operation, reason);
        return new ApiException(ErrorCode.AI_QUOTA_EXCEEDED, message,
                details(reason, operation, limit, used, retryAfter == null ? null : clock.instant().plus(retryAfter),
                        retryAfter),
                retryAfter, null);
    }

    private static Map<String, Object> details(String reason, AIOperation operation, long limit, Long used,
                                               Instant resetsAt, Duration retryAfter) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("operation", operation.name());
        details.put("limit", limit);
        if (used != null) {
            details.put("used", used);
        }
        if (resetsAt != null) {
            details.put("resetsAt", resetsAt.truncatedTo(ChronoUnit.SECONDS).toString());
        }
        details.put("retryAfterSeconds", seconds(retryAfter));
        return details;
    }

    private static long seconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }

    private static String format(Duration period) {
        long seconds = period.toSeconds();
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        return seconds % 60 == 0 ? (seconds / 60) + "m" : seconds + "s";
    }
}
