package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.dto.ErrorAnalysisResult;
import com.samsenpro.aiassistant.ai.context.UntrustedContentRenderer;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.file.FileSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ErrorAnalysisHandler implements OperationHandler<ErrorAnalysisResult> {

    private final UntrustedContentRenderer renderer;

    public ErrorAnalysisHandler(UntrustedContentRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public AIOperation operation() {
        return AIOperation.ANALYZE_ERROR;
    }

    @Override
    public PromptId promptId() {
        return PromptId.ERROR_ANALYSIS;
    }

    @Override
    public Class<ErrorAnalysisResult> resultType() {
        return ErrorAnalysisResult.class;
    }

    @Override
    public void validate(AIOperationCommand command, List<FileSnapshot> files) {
        if (command.error() == null || command.error().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "The error message is required");
        }
        if (command.hasFiles() && command.projectId() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "projectId is required when fileIds are provided");
        }
    }

    /** El mensaje, el contexto y la traza son texto no fiable: van en bloques de datos delimitados. */
    @Override
    public Map<String, String> variables(OperationInput input) {
        AIOperationCommand command = input.command();
        StringBuilder details = new StringBuilder();
        details.append(renderer.renderText(input.boundary(), "error_message", command.error()));
        if (command.errorContext() != null) {
            details.append(renderer.renderText(input.boundary(), "context", command.errorContext()));
        }
        if (command.stackTrace() != null) {
            details.append(renderer.renderText(input.boundary(), "stack_trace", command.stackTrace()));
        }
        return Map.of("errorDetails", details.toString());
    }
}
