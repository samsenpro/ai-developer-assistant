package com.samsenpro.aiassistant.ai.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Valida las plantillas reales de src/main/resources/prompts. */
class PromptServiceTest {

    private final PromptService promptService = new PromptService();

    @ParameterizedTest
    @EnumSource(PromptId.class)
    void everyTemplateIsVersionedAndHasTheRequiredSections(PromptId id) {
        PromptTemplate template = promptService.get(id);

        assertThat(template.version()).matches("\\d+\\.\\d+\\.\\d+");
        assertThat(template.sectionNames()).first().isEqualTo("SYSTEM");
        assertThat(template.sectionNames()).contains("CONTEXT", "SOURCE CODE", "TASK", "OUTPUT FORMAT");
    }

    @ParameterizedTest
    @EnumSource(PromptId.class)
    void untrustedContentNeverGoesIntoTheSystemMessage(PromptId id) {
        PromptTemplate template = promptService.get(id);
        Map<String, String> values = new HashMap<>();
        template.variables().forEach(variable -> values.put(variable, "<<VALUE:" + variable + ">>"));

        PromptTemplate.Rendered rendered = template.render(values);

        assertThat(rendered.system()).doesNotContain("<<VALUE:");
        assertThat(rendered.system()).containsIgnoringCase("untrusted");
        if (template.variables().contains("sourceCode")) {
            assertThat(rendered.user()).contains("<<VALUE:sourceCode>>");
        }
    }

    @Test
    void structuredOperationsAskForJsonAndChatForMarkdown() {
        for (PromptId id : PromptId.values()) {
            String output = promptService.get(id).render(dummyValues(id)).user();
            if (id == PromptId.CHAT) {
                assertThat(output).contains("Markdown");
            } else {
                assertThat(output).contains("Return a JSON object");
            }
        }
    }

    @Test
    void reviewPromptForbidsInventingLineNumbers() {
        String system = promptService.get(PromptId.REVIEW).render(dummyValues(PromptId.REVIEW)).system();

        assertThat(system).contains("use null").contains("Never guess or invent line numbers");
    }

    @Test
    void errorAnalysisPromptRequiresHedgedLanguage() {
        String system = promptService.get(PromptId.ERROR_ANALYSIS).render(dummyValues(PromptId.ERROR_ANALYSIS)).system();

        assertThat(system).contains("probable cause").contains("Never state a hypothesis as a certainty");
    }

    private Map<String, String> dummyValues(PromptId id) {
        Map<String, String> values = new HashMap<>();
        promptService.get(id).variables().forEach(variable -> values.put(variable, "x"));
        return values;
    }
}
