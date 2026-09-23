package com.samsenpro.aiassistant.ai.prompt;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.context.ContextProperties;
import com.samsenpro.aiassistant.ai.context.TokenEstimator;
import com.samsenpro.aiassistant.ai.provider.AIPrompt;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Construye el {@link AIPrompt} final a partir de una plantilla y sus variables, y aplica el
 * límite de tamaño total del prompt (última barrera antes de gastar tokens).
 */
@Component
public class PromptBuilder {

    private final PromptService promptService;
    private final TokenEstimator tokenEstimator;
    private final int maxPromptTokens;

    public PromptBuilder(PromptService promptService, TokenEstimator tokenEstimator, ContextProperties properties) {
        this.promptService = promptService;
        this.tokenEstimator = tokenEstimator;
        this.maxPromptTokens = properties.maxPromptTokens();
    }

    public AIPrompt build(PromptId promptId, AIOperation operation, Map<String, String> variables, int maxOutputTokens) {
        PromptTemplate template = promptService.get(promptId);
        PromptTemplate.Rendered rendered = template.render(variables);
        AIPrompt prompt = new AIPrompt(operation, template.id(), template.version(), rendered.system(),
                rendered.user(), maxOutputTokens);
        int estimated = estimateInputTokens(prompt);
        if (estimated > maxPromptTokens) {
            throw new ApiException(ErrorCode.CONTEXT_TOO_LARGE,
                    "The request is too large for the AI context limit. Select fewer or smaller files, "
                            + "or shorten the text.",
                    Map.of("estimatedTokens", estimated, "maxPromptTokens", maxPromptTokens));
        }
        return prompt;
    }

    public int estimateInputTokens(AIPrompt prompt) {
        return tokenEstimator.estimate(prompt.system()) + tokenEstimator.estimate(prompt.user());
    }

    public String version(PromptId promptId) {
        return promptService.get(promptId).version();
    }
}
