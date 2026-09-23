package com.samsenpro.aiassistant.job;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.dto.DocumentationType;
import com.samsenpro.aiassistant.ai.analysis.dto.ImprovementGoal;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Petición de un job: la operación y los campos de la petición síncrona equivalente.
 * <ul>
 *     <li>EXPLAIN, REVIEW, GENERATE_TESTS: projectId, fileIds</li>
 *     <li>IMPROVE: además goals</li>
 *     <li>GENERATE_DOCUMENTATION: además documentationType</li>
 *     <li>ANALYZE_ERROR: error (projectId, fileIds, context y stackTrace opcionales)</li>
 * </ul>
 */
public record JobRequest(
        @Schema(example = "REVIEW") @NotNull AIOperation operation,
        @Schema(example = "1") Long projectId,
        @Schema(example = "[10, 11]") @Size(max = 50) List<@NotNull Long> fileIds,
        @Schema(nullable = true) ProgrammingLanguage language,
        @Schema(nullable = true) @Size(max = 2000) String instructions,
        @Schema(nullable = true) Boolean includeRelatedFiles,
        @Schema(nullable = true) Set<@NotNull ImprovementGoal> goals,
        @Schema(nullable = true) DocumentationType documentationType,
        @Schema(nullable = true) @Size(max = 5000) String error,
        @Schema(nullable = true) @Size(max = 5000) String context,
        @Schema(nullable = true) @Size(max = 20000) String stackTrace) {

    public AIOperationCommand toCommand() {
        if (operation == AIOperation.CHAT) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Conversations use /api/conversations/{id}/messages");
        }
        boolean codeOperation = operation != AIOperation.ANALYZE_ERROR;
        if (codeOperation && (projectId == null || fileIds == null || fileIds.isEmpty())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, operation + " requires projectId and fileIds");
        }
        if (operation == AIOperation.IMPROVE && (goals == null || goals.isEmpty())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "IMPROVE requires goals");
        }
        if (operation == AIOperation.GENERATE_DOCUMENTATION && documentationType == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "GENERATE_DOCUMENTATION requires documentationType");
        }
        if (operation == AIOperation.ANALYZE_ERROR && (error == null || error.isBlank())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "ANALYZE_ERROR requires error");
        }
        return new AIOperationCommand(operation, projectId, fileIds, codeOperation ? language : null,
                codeOperation ? instructions : null, codeOperation && !Boolean.FALSE.equals(includeRelatedFiles),
                operation == AIOperation.IMPROVE ? List.copyOf(goals) : null,
                operation == AIOperation.GENERATE_DOCUMENTATION ? documentationType : null,
                codeOperation ? null : error, codeOperation ? null : context, codeOperation ? null : stackTrace);
    }
}
