package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.dto.DocumentationResult;
import com.samsenpro.aiassistant.ai.analysis.dto.ErrorAnalysisResult;
import com.samsenpro.aiassistant.ai.analysis.dto.ExplanationResult;
import com.samsenpro.aiassistant.ai.analysis.dto.ImprovementResult;
import com.samsenpro.aiassistant.ai.analysis.dto.ReviewResult;
import com.samsenpro.aiassistant.ai.analysis.dto.TestGenerationResult;
import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.IdempotencyKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones de IA síncronas: la respuesta llega cuando el modelo termina. Para operaciones
 * largas usar POST /api/ai/jobs.
 * <p>
 * Cabeceras opcionales comunes:
 * <ul>
 *     <li>{@code Idempotency-Key}: repetir la petición con la misma clave devuelve el mismo resultado
 *     sin volver a llamar al LLM;</li>
 *     <li>{@code Cache-Control: no-cache}: fuerza un análisis nuevo aunque exista uno idéntico.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Operations", description = "Explicar, revisar, mejorar, documentar, generar tests y analizar errores")
public class AIOperationController {

    private final AnalysisService analysisService;

    public AIOperationController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/explain")
    @Operation(summary = "Explicar código",
            description = "Propósito, estructura, responsabilidades, flujo, dependencias y puntos importantes.")
    public ApiResponse<AnalysisView<ExplanationResult>> explain(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CodeOperationRequest request,
            @Parameter(description = "Clave de idempotencia (se recomienda un UUID)")
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @Parameter(description = "no-cache para forzar un análisis nuevo")
            @RequestHeader(value = HttpHeaders.CACHE_CONTROL, required = false) String cacheControl) {
        return ApiResponse.ok(analysisService.execute(user.id(), AIOperationCommand.code(AIOperation.EXPLAIN, request),
                options(idempotencyKey, cacheControl), ExplanationResult.class));
    }

    @PostMapping("/review")
    @Operation(summary = "Revisión de código",
            description = "Bugs, code smells, seguridad, rendimiento, mantenibilidad, duplicación y malas prácticas, "
                    + "con severidad LOW/MEDIUM/HIGH/CRITICAL. Las líneas se validan contra el archivo real: si el "
                    + "modelo no puede determinarla, line es null.")
    public ApiResponse<AnalysisView<ReviewResult>> review(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CodeOperationRequest request,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.CACHE_CONTROL, required = false) String cacheControl) {
        return ApiResponse.ok(analysisService.execute(user.id(), AIOperationCommand.code(AIOperation.REVIEW, request),
                options(idempotencyKey, cacheControl), ReviewResult.class));
    }

    @PostMapping("/improve")
    @Operation(summary = "Proponer mejoras",
            description = "Objetivos: performance, readability, clean-code, security, architecture. Devuelve análisis, "
                    + "cambios sugeridos, código mejorado y explicación. No modifica los archivos del proyecto.")
    public ApiResponse<AnalysisView<ImprovementResult>> improve(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ImproveRequest request,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.CACHE_CONTROL, required = false) String cacheControl) {
        return ApiResponse.ok(analysisService.execute(user.id(), AIOperationCommand.improve(request),
                options(idempotencyKey, cacheControl), ImprovementResult.class));
    }

    @PostMapping("/generate-tests")
    @Operation(summary = "Generar tests",
            description = "Java: JUnit 5 + Mockito; Python: pytest; JavaScript/TypeScript: Jest, Vitest u otra "
                    + "estrategia según el framework detectado.")
    public ApiResponse<AnalysisView<TestGenerationResult>> generateTests(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CodeOperationRequest request,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.CACHE_CONTROL, required = false) String cacheControl) {
        return ApiResponse.ok(analysisService.execute(user.id(),
                AIOperationCommand.code(AIOperation.GENERATE_TESTS, request), options(idempotencyKey, cacheControl),
                TestGenerationResult.class));
    }

    @PostMapping("/documentation")
    @Operation(summary = "Generar documentación",
            description = "Tipos: JAVADOC, CLASS, METHOD, README, API, ARCHITECTURE.")
    public ApiResponse<AnalysisView<DocumentationResult>> documentation(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DocumentationRequest request,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.CACHE_CONTROL, required = false) String cacheControl) {
        return ApiResponse.ok(analysisService.execute(user.id(), AIOperationCommand.documentation(request),
                options(idempotencyKey, cacheControl), DocumentationResult.class));
    }

    @PostMapping("/analyze-error")
    @Operation(summary = "Analizar un error",
            description = "Causa probable (como hipótesis, con nivel de confianza), explicación, posibles soluciones "
                    + "y prevención. projectId y fileIds son opcionales.")
    public ApiResponse<AnalysisView<ErrorAnalysisResult>> analyzeError(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ErrorAnalysisRequest request,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.CACHE_CONTROL, required = false) String cacheControl) {
        return ApiResponse.ok(analysisService.execute(user.id(), AIOperationCommand.errorAnalysis(request),
                options(idempotencyKey, cacheControl), ErrorAnalysisResult.class));
    }

    private static AnalysisService.ExecutionOptions options(String idempotencyKey, String cacheControl) {
        boolean noCache = cacheControl != null && cacheControl.toLowerCase().contains("no-cache");
        return AnalysisService.ExecutionOptions.sync(IdempotencyKeys.validate(idempotencyKey), !noCache);
    }
}
