package com.samsenpro.aiassistant.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.AnalysisResultRepository;
import com.samsenpro.aiassistant.ai.analysis.AnalysisService;
import com.samsenpro.aiassistant.ai.analysis.AnalysisView;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.web.CorrelationId;
import com.samsenpro.aiassistant.common.web.PageRequests;
import com.samsenpro.aiassistant.common.web.PageResponse;
import com.samsenpro.aiassistant.usage.UsageLimitsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jobs de análisis asíncronos: el cliente recibe un jobId al momento (202) y consulta el estado
 * hasta que termina. La validación (autorización, archivos, rate limit) se hace al crear el job,
 * para rechazar enseguida lo que fallaría después.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final List<JobStatus> ACTIVE = List.of(JobStatus.PENDING, JobStatus.PROCESSING);

    private final AnalysisJobRepository jobRepository;
    private final AnalysisResultRepository resultRepository;
    private final AnalysisService analysisService;
    private final UsageLimitsProperties limits;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobService(AnalysisJobRepository jobRepository, AnalysisResultRepository resultRepository,
                      AnalysisService analysisService, UsageLimitsProperties limits, ApplicationEventPublisher events,
                      ObjectMapper objectMapper, Clock clock) {
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
        this.analysisService = analysisService;
        this.limits = limits;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** @param created false si la Idempotency-Key ya correspondía a un job (se devuelve ese job) */
    public record Submission(JobResponse job, boolean created) {
    }

    @Transactional
    public Submission submit(Long userId, JobRequest request, String idempotencyKey) {
        AIOperationCommand command = request.toCommand();
        String fingerprint = analysisService.fingerprint(command);

        if (idempotencyKey != null) {
            var existing = jobRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                    throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                return new Submission(view(existing.get()), false);
            }
        }

        analysisService.precheck(userId, command);
        long active = jobRepository.countByUserIdAndStatusIn(userId, ACTIVE);
        if (active >= limits.maxPendingJobs()) {
            throw new ApiException(ErrorCode.AI_RATE_LIMIT,
                    "You already have %d jobs pending or in progress. Wait until they finish.".formatted(active),
                    Map.of("reason", "TOO_MANY_PENDING_JOBS", "limit", limits.maxPendingJobs()));
        }

        JsonNode stored = objectMapper.valueToTree(command);
        AnalysisJob job = new AnalysisJob(userId, command.projectId(), command.operation(), stored, idempotencyKey,
                fingerprint, MDC.get(CorrelationId.MDC_KEY), clock.instant());
        try {
            job = jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
        // El job se encola cuando la transacción confirma (ver JobDispatcher): nunca antes de existir en la BD
        events.publishEvent(new JobSubmittedEvent(job.getId()));
        log.info("Job {} submitted: operation={} userId={}", job.getId(), job.getOperation(), userId);
        return new Submission(view(job), true);
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long userId, UUID jobId) {
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.JOB_NOT_FOUND));
        if (!job.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_RESOURCE);
        }
        return view(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(Long userId, int page, int size) {
        return PageResponse.of(jobRepository.findByUserId(userId,
                        PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                job -> JobResponse.of(job, null));
    }

    private JobResponse view(AnalysisJob job) {
        AnalysisView<JsonNode> analysis = job.getStatus() == JobStatus.COMPLETED && job.getResultId() != null
                ? resultRepository.findById(job.getResultId())
                .map(result -> analysisService.toView(result, JsonNode.class, false))
                .orElse(null)
                : null;
        return JobResponse.of(job, analysis);
    }

    public record JobSubmittedEvent(UUID jobId) {
    }
}
