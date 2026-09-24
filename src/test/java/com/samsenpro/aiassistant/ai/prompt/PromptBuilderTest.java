package com.samsenpro.aiassistant.ai.prompt;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.context.ContextProperties;
import com.samsenpro.aiassistant.ai.context.TokenEstimator;
import com.samsenpro.aiassistant.ai.provider.AIPrompt;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptBuilderTest {

    private final PromptService promptService = new PromptService();

    @Test
    void buildsThePromptWithTemplateVersionAndOutputLimit() {
        PromptBuilder builder = builder(16_000);

        AIPrompt prompt = builder.build(PromptId.REVIEW, AIOperation.REVIEW, values("class A {}"), 3000);

        assertThat(prompt.operation()).isEqualTo(AIOperation.REVIEW);
        assertThat(prompt.promptId()).isEqualTo("review");
        assertThat(prompt.promptVersion()).isEqualTo(promptService.get(PromptId.REVIEW).version());
        assertThat(prompt.maxOutputTokens()).isEqualTo(3000);
        assertThat(prompt.user()).contains("class A {}");
        assertThat(prompt.toString()).doesNotContain("class A {}");
    }

    @Test
    void rejectsPromptsAboveTheConfiguredLimitBeforeCallingTheProvider() {
        PromptBuilder builder = builder(2_000);

        assertThatThrownBy(() -> builder.build(PromptId.REVIEW, AIOperation.REVIEW, values("x".repeat(20_000)), 1000))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.code()).isEqualTo(ErrorCode.CONTEXT_TOO_LARGE);
                    assertThat(api.details()).containsEntry("maxPromptTokens", 2000);
                });
    }

    private PromptBuilder builder(int maxPromptTokens) {
        ContextProperties properties = new ContextProperties(12_000, maxPromptTokens, 4.0, 10, 5, 2, 4, true);
        return new PromptBuilder(promptService, new TokenEstimator(properties), properties);
    }

    private Map<String, String> values(String source) {
        Map<String, String> values = new HashMap<>();
        promptService.get(PromptId.REVIEW).variables().forEach(variable -> values.put(variable, ""));
        values.put("sourceCode", source);
        return values;
    }
}
