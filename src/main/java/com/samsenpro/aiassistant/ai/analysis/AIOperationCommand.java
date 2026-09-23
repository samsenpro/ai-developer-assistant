package com.samsenpro.aiassistant.ai.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.dto.DocumentationType;
import com.samsenpro.aiassistant.ai.analysis.dto.ImprovementGoal;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;

import java.util.Collection;
import java.util.List;

/**
 * Operación de IA normalizada, independiente de por dónde llegó (endpoint síncrono o job). Es lo
 * que se guarda en analysis_jobs.request y de lo que se calcula la huella para la idempotencia.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record AIOperationCommand(
        AIOperation operation,
        Long projectId,
        List<Long> fileIds,
        ProgrammingLanguage language,
        String instructions,
        boolean includeRelatedFiles,
        List<ImprovementGoal> goals,
        DocumentationType documentationType,
        String error,
        String errorContext,
        String stackTrace) {

    public AIOperationCommand {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
        // Orden estable: {SECURITY, READABILITY} y {READABILITY, SECURITY} son la misma petición
        goals = goals == null ? null : goals.stream().distinct().sorted().toList();
        instructions = blankToNull(instructions);
        errorContext = blankToNull(errorContext);
        stackTrace = blankToNull(stackTrace);
    }

    public static AIOperationCommand code(AIOperation operation, CodeOperationRequest request) {
        return new AIOperationCommand(operation, request.projectId(), request.fileIds(), request.language(),
                request.instructions(), !Boolean.FALSE.equals(request.includeRelatedFiles()), null, null, null, null,
                null);
    }

    public static AIOperationCommand improve(ImproveRequest request) {
        return new AIOperationCommand(AIOperation.IMPROVE, request.projectId(), request.fileIds(), request.language(),
                request.instructions(), !Boolean.FALSE.equals(request.includeRelatedFiles()), toList(request.goals()),
                null, null, null, null);
    }

    public static AIOperationCommand documentation(DocumentationRequest request) {
        return new AIOperationCommand(AIOperation.GENERATE_DOCUMENTATION, request.projectId(), request.fileIds(),
                request.language(), request.instructions(), !Boolean.FALSE.equals(request.includeRelatedFiles()), null,
                request.type(), null, null, null);
    }

    public static AIOperationCommand errorAnalysis(ErrorAnalysisRequest request) {
        return new AIOperationCommand(AIOperation.ANALYZE_ERROR, request.projectId(), request.fileIds(), null, null,
                false, null, null, request.error(), request.context(), request.stackTrace());
    }

    public boolean hasFiles() {
        return !fileIds.isEmpty();
    }

    private static <T> List<T> toList(Collection<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
