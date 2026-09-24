package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;

import java.time.Instant;

public record UsageRecordView(Long id, Long projectId, AIOperation operation, String provider, String model,
                              int inputTokens, int outputTokens, int totalTokens, boolean tokensEstimated,
                              long durationMs, UsageStatus status, String errorCode, Instant createdAt) {

    public static UsageRecordView from(AIUsage usage) {
        return new UsageRecordView(usage.getId(), usage.getProjectId(), usage.getOperation(), usage.getProvider(),
                usage.getModel(), usage.getInputTokens(), usage.getOutputTokens(), usage.getTotalTokens(),
                usage.isTokensEstimated(), usage.getDurationMs(), usage.getStatus(), usage.getErrorCode(),
                usage.getCreatedAt());
    }
}
