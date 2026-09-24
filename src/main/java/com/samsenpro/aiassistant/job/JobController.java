package com.samsenpro.aiassistant.job;

import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.IdempotencyKeys;
import com.samsenpro.aiassistant.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/jobs")
@Tag(name = "Jobs", description = "Operaciones de IA asíncronas: crear el job y consultar su estado")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @Operation(summary = "Crear un job de análisis",
            description = "Devuelve 202 con el jobId en estado PENDING; el resultado se consulta con GET /api/ai/jobs/{jobId}. "
                    + "Con una Idempotency-Key ya usada devuelve 200 con el job existente.")
    public ResponseEntity<ApiResponse<JobResponse>> submit(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody JobRequest request,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey) {
        JobService.Submission submission = jobService.submit(user.id(), request, IdempotencyKeys.validate(idempotencyKey));
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(submission.job().jobId()).toUri();
        return ResponseEntity.status(submission.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(location)
                .body(ApiResponse.ok(submission.job()));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Consultar un job", description = "Incluye el análisis cuando está COMPLETED y el error cuando está FAILED.")
    public ApiResponse<JobResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID jobId) {
        return ApiResponse.ok(jobService.get(user.id(), jobId));
    }

    @GetMapping
    @Operation(summary = "Listar mis jobs", description = "Del más reciente al más antiguo, sin el resultado.")
    public ApiResponse<PageResponse<JobResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(jobService.list(user.id(), page, size));
    }
}
