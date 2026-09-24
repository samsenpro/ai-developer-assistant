package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Una fila por llamada al proveedor. Solo metadatos de consumo: ni el prompt ni la respuesta, que
 * pueden contener código o datos sensibles del usuario.
 */
@Entity
@Table(name = "ai_usage")
public class AIUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AIOperation operation;

    @Column(nullable = false, length = 50, updatable = false)
    private String provider;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false, updatable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false, updatable = false)
    private int outputTokens;

    @Column(name = "total_tokens", nullable = false, updatable = false)
    private int totalTokens;

    @Column(name = "tokens_estimated", nullable = false, updatable = false)
    private boolean tokensEstimated;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private UsageStatus status;

    @Column(name = "error_code", length = 50, updatable = false)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AIUsage() {
    }

    public AIUsage(UsageRecord record, Instant now) {
        this.userId = record.userId();
        this.projectId = record.projectId();
        this.operation = record.operation();
        this.provider = record.provider();
        this.model = record.model();
        this.inputTokens = record.inputTokens();
        this.outputTokens = record.outputTokens();
        this.totalTokens = record.inputTokens() + record.outputTokens();
        this.tokensEstimated = record.tokensEstimated();
        this.durationMs = record.durationMs();
        this.status = record.status();
        this.errorCode = record.errorCode();
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public AIOperation getOperation() {
        return operation;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public boolean isTokensEstimated() {
        return tokensEstimated;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public UsageStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
