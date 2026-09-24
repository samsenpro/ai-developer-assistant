package com.samsenpro.aiassistant.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.samsenpro.aiassistant.ai.AIOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Resultado persistido de una operación de IA. Se crea en PROCESSING antes de llamar al proveedor
 * (así la Idempotency-Key queda reservada) y pasa a COMPLETED o FAILED.
 */
@Entity
@Table(name = "analysis_results")
public class AnalysisResult {

    private static final int MAX_ERROR_MESSAGE = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AIOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_ids", nullable = false)
    private List<Long> fileIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_files", nullable = false)
    private JsonNode contextFiles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private JsonNode result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false)
    private List<String> warnings;

    @Column(length = 50)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(nullable = false)
    private boolean cached;

    @Column(name = "source_result_id")
    private Long sourceResultId;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AnalysisResult() {
    }

    public static AnalysisResult processing(Long userId, Long projectId, AIOperation operation, List<Long> fileIds,
                                            JsonNode contextFiles, String inputHash, String promptVersion,
                                            String idempotencyKey, String requestFingerprint, Instant now) {
        AnalysisResult analysis = new AnalysisResult();
        analysis.userId = userId;
        analysis.projectId = projectId;
        analysis.operation = operation;
        analysis.status = AnalysisStatus.PROCESSING;
        analysis.fileIds = List.copyOf(fileIds);
        analysis.contextFiles = contextFiles;
        analysis.inputHash = inputHash;
        analysis.promptVersion = promptVersion;
        analysis.idempotencyKey = idempotencyKey;
        analysis.requestFingerprint = requestFingerprint;
        analysis.warnings = List.of();
        analysis.createdAt = now;
        return analysis;
    }

    /**
     * Resultado servido desde la caché: mismo contenido que el original, sin llamada al LLM ni
     * consumo de tokens. Queda en el historial marcado como cached y enlazado al original.
     */
    public void completeFromCache(AnalysisCache.CachedAnalysis source, List<String> warnings, Instant now) {
        this.status = AnalysisStatus.COMPLETED;
        this.result = source.result();
        this.warnings = List.copyOf(warnings);
        this.provider = source.provider();
        this.model = source.model();
        this.cached = true;
        this.sourceResultId = source.id();
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.completedAt = now;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void complete(JsonNode result, List<String> warnings, String provider, String model, int inputTokens,
                         int outputTokens, Instant now) {
        this.status = AnalysisStatus.COMPLETED;
        this.result = result;
        this.warnings = List.copyOf(warnings);
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.completedAt = now;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void fail(String errorCode, String errorMessage, Instant now) {
        this.status = AnalysisStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE
                ? errorMessage : errorMessage.substring(0, MAX_ERROR_MESSAGE);
        this.completedAt = now;
    }

    /** Un reintento con la misma Idempotency-Key tras un fallo reutiliza la fila. */
    public void restart(String inputHash, String promptVersion, List<Long> fileIds, JsonNode contextFiles) {
        this.status = AnalysisStatus.PROCESSING;
        this.inputHash = inputHash;
        this.promptVersion = promptVersion;
        this.fileIds = List.copyOf(fileIds);
        this.contextFiles = contextFiles;
        this.errorCode = null;
        this.errorMessage = null;
        this.completedAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public AIOperation getOperation() {
        return operation;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public List<Long> getFileIds() {
        return fileIds;
    }

    public JsonNode getContextFiles() {
        return contextFiles;
    }

    public JsonNode getResult() {
        return result;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public boolean isCached() {
        return cached;
    }

    public Long getSourceResultId() {
        return sourceResultId;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
