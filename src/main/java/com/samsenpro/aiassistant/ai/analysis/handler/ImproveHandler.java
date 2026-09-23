package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.dto.ImprovementGoal;
import com.samsenpro.aiassistant.ai.analysis.dto.ImprovementResult;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.file.FileSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ImproveHandler implements OperationHandler<ImprovementResult> {

    @Override
    public AIOperation operation() {
        return AIOperation.IMPROVE;
    }

    @Override
    public PromptId promptId() {
        return PromptId.IMPROVE;
    }

    @Override
    public Class<ImprovementResult> resultType() {
        return ImprovementResult.class;
    }

    @Override
    public void validate(AIOperationCommand command, List<FileSnapshot> files) {
        if (command.goals() == null || command.goals().isEmpty() || command.goals().contains(ImprovementGoal.OTHER)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Improvement goals must be one or more of: performance, readability, clean-code, security, architecture");
        }
    }

    @Override
    public Map<String, String> variables(OperationInput input) {
        return Map.of("goals", input.command().goals().stream()
                .map(goal -> goal.name().toLowerCase().replace('_', '-'))
                .collect(Collectors.joining(", ")));
    }

    /** El código propuesto no se aplica: solo se limpia (prefijos de número de línea copiados del prompt). */
    @Override
    public ImprovementResult postProcess(ImprovementResult result, OperationInput input, List<String> warnings) {
        Map<String, Integer> known = GeneratedCode.knownFiles(input.context());
        List<ImprovementResult.ImprovedFile> improved = result.improvedCode().stream()
                .map(file -> new ImprovementResult.ImprovedFile(
                        GeneratedCode.resolvePath(file.file(), known).orElse(file.file()),
                        GeneratedCode.stripLineNumbers(file.content())))
                .toList();
        long newFiles = improved.stream().filter(file -> !known.containsKey(file.file())).count();
        if (newFiles > 0) {
            warnings.add("%d improved file(s) do not correspond to an existing file (for example, a newly extracted class)."
                    .formatted(newFiles));
        }
        return new ImprovementResult(result.originalAnalysis(), result.suggestedChanges(), improved,
                result.explanation());
    }
}
