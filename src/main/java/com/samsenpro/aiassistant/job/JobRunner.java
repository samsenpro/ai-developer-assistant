package com.samsenpro.aiassistant.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.AnalysisResult;
import com.samsenpro.aiassistant.ai.analysis.AnalysisService;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.web.CorrelationId;
import com.samsenpro.aiassistant.usage.AIMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** Ejecuta un job en un hilo del pool de jobs. */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final AnalysisJobRepository repository;
    private final AnalysisService analysisService;
    private final AIMetrics metrics;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobRunner(AnalysisJobRepository repository, AnalysisService analysisService, AIMetrics metrics,
                     ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void run(UUID jobId) {
        if (repository.claim(jobId, clock.instant()) == 0) {
            log.debug("Job {} was already claimed or is no longer pending", jobId);
            return;
        }
        AnalysisJob job = repository.findById(jobId).orElseThrow();
        // Los logs del job se pueden buscar con el correlation ID de la petición que lo creó
        if (job.getCorrelationId() != null) {
            MDC.put(CorrelationId.MDC_KEY, job.getCorrelationId());
        }
        try {
            AIOperationCommand command = objectMapper.treeToValue(job.getRequest(), AIOperationCommand.class);
            AnalysisResult result = analysisService.executeForJob(job.getUserId(), command);
            job.complete(result.getId(), clock.instant());
            log.info("Job {} completed with analysis {}", jobId, result.getId());
        } catch (ApiException ex) {
            job.fail(ex.code().name(), ex.getMessage(), clock.instant());
            log.warn("Job {} failed: {}", jobId, ex.code());
        } catch (JsonProcessingException | RuntimeException ex) {
            job.fail(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage(), clock.instant());
            log.error("Job {} failed unexpectedly", jobId, ex);
        } finally {
            repository.save(job);
            metrics.recordJob(job.getOperation(), job.getStatus().name());
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    /** Un job que no se pudo encolar (cola llena) termina en FAILED en lugar de quedar PENDING para siempre. */
    public void reject(UUID jobId) {
        repository.findById(jobId).ifPresent(job -> {
            job.fail(ErrorCode.AI_PROVIDER_UNAVAILABLE.name(), "The job queue is full, try again later", clock.instant());
            repository.save(job);
            metrics.recordJob(job.getOperation(), job.getStatus().name());
        });
    }
}
