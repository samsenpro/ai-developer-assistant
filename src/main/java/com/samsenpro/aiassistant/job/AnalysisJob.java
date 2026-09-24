package com.samsenpro.aiassistant.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.samsenpro.aiassistant.ai.AIOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Operación de IA ejecutada en segundo plano. Guarda la petición normalizada para poder ejecutarla
 * (o reanudarla tras un reinicio) y, al terminar, la referencia al resultado persistido.
 */
@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    private static final int MAX_ERROR_MESSAGE = 500;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AIOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private JsonNode request;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE)
    private String errorMessage;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AnalysisJob() {
    }

    public AnalysisJob(Long userId, Long projectId, AIOperation operation, JsonNode request, String idempotencyKey,
                       String requestFingerprint, String correlationId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.projectId = projectId;
        this.operation = operation;
        this.status = JobStatus.PENDING;
        this.request = request;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.correlationId = correlationId;
        this.createdAt = now;
    }

    public void complete(Long resultId, Instant now) {
        this.status = JobStatus.COMPLETED;
        this.resultId = resultId;
        this.completedAt = now;
    }

    public void fail(String errorCode, String errorMessage, Instant now) {
        this.status = JobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE
                ? errorMessage : errorMessage.substring(0, MAX_ERROR_MESSAGE);
        this.completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public AIOperation getOperation() {
        return operation;
    }

    public JobStatus getStatus() {
        return status;
    }

    public JsonNode getRequest() {
        return request;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Long getResultId() {
        return resultId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
