package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.dto.ExplanationResult;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import org.springframework.stereotype.Component;

@Component
public class ExplainHandler implements OperationHandler<ExplanationResult> {

    @Override
    public AIOperation operation() {
        return AIOperation.EXPLAIN;
    }

    @Override
    public PromptId promptId() {
        return PromptId.EXPLAIN;
    }

    @Override
    public Class<ExplanationResult> resultType() {
        return ExplanationResult.class;
    }
}
