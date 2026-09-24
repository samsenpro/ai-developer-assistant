package com.samsenpro.aiassistant.usage;

import java.time.Instant;
import java.util.List;

/** Consumo de IA de un usuario en un periodo, con el estado de su cuota diaria. */
public record UsageSummary(Instant from, Instant to, Totals totals, List<OperationUsage> byOperation, Quota quota) {

    public record Totals(long requests, long failedRequests, long inputTokens, long outputTokens, long totalTokens) {
    }

    public record Quota(long dailyTokenLimit, long tokensUsedToday, long tokensRemainingToday,
                        long dailyRequestLimit, long requestsToday, long requestsRemainingToday, Instant resetsAt) {
    }
}
