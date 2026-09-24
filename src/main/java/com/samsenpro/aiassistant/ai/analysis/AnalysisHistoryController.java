package com.samsenpro.aiassistant.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Analysis History", description = "Historial de análisis de IA de un proyecto")
public class AnalysisHistoryController {

    private final AnalysisService analysisService;

    public AnalysisHistoryController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/api/projects/{projectId}/analyses")
    @Operation(summary = "Historial de análisis de un proyecto", description = "Del más reciente al más antiguo; filtrable por operación.")
    public ApiResponse<PageResponse<AnalysisSummary>> history(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @PathVariable Long projectId,
                                                              @RequestParam(required = false) AIOperation operation,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(analysisService.history(user.id(), projectId, operation, page, size));
    }

    @GetMapping("/api/analyses/{id}")
    @Operation(summary = "Obtener un análisis con su resultado completo")
    public ApiResponse<AnalysisView<JsonNode>> get(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(analysisService.get(user.id(), id));
    }
}
