package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/usage")
@Tag(name = "Usage", description = "Consumo de IA del usuario autenticado: tokens, peticiones y cuotas")
public class UsageController {

    private static final Duration MAX_RANGE = Duration.ofDays(366);

    private final AIUsageService usageService;
    private final Clock clock;

    public UsageController(AIUsageService usageService, Clock clock) {
        this.usageService = usageService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Resumen de consumo",
            description = "Totales y desglose por operación en el periodo (por defecto, los últimos 30 días), "
                    + "más el estado de la cuota diaria.")
    public ApiResponse<UsageSummary> summary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "2026-09-01T00:00:00Z") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(example = "2026-10-01T00:00:00Z") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        if (!start.isBefore(end) || Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "'from' must be before 'to' and the range at most 366 days");
        }
        return ApiResponse.ok(usageService.summary(user.id(), start, end));
    }

    @GetMapping("/records")
    @Operation(summary = "Detalle de llamadas al proveedor", description = "Una fila por llamada, de la más reciente a la más antigua.")
    public ApiResponse<PageResponse<UsageRecordView>> records(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(usageService.records(user.id(), page, size));
    }
}
