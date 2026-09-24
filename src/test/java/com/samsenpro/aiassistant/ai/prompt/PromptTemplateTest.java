package com.samsenpro.aiassistant.ai.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    private static final String TEMPLATE = """
            # id: sample
            # version: 2.1.0
            === SYSTEM ===
            You are a reviewer. Language: {{language}}
            === CONTEXT ===
            Project: {{projectName}}
            === SOURCE CODE ===
            {{sourceCode}}
            === TASK ===
            Review it.
            === OUTPUT FORMAT ===
            JSON
            """;

    @Test
    void parsesHeadersAndSectionsInOrder() {
        PromptTemplate template = PromptTemplate.parse("sample.txt", TEMPLATE);

        assertThat(template.id()).isEqualTo("sample");
        assertThat(template.version()).isEqualTo("2.1.0");
        assertThat(template.sectionNames())
                .containsExactly("SYSTEM", "CONTEXT", "SOURCE CODE", "TASK", "OUTPUT FORMAT");
        assertThat(template.variables()).containsExactlyInAnyOrder("language", "projectName", "sourceCode");
    }

    @Test
    void systemSectionGoesToSystemMessageAndTheRestToUserMessageWithHeaders() {
        PromptTemplate.Rendered rendered = PromptTemplate.parse("sample.txt", TEMPLATE)
                .render(Map.of("language", "Java", "projectName", "shop", "sourceCode", "class A {}"));

        assertThat(rendered.system()).isEqualTo("You are a reviewer. Language: Java");
        assertThat(rendered.user())
                .startsWith("### CONTEXT\nProject: shop")
                .contains("### SOURCE CODE\nclass A {}")
                .contains("### TASK\nReview it.")
                .endsWith("### OUTPUT FORMAT\nJSON")
                .doesNotContain("You are a reviewer");
    }

    @Test
    void substitutesInASinglePassSoUserContentCannotInjectVariables() {
        PromptTemplate.Rendered rendered = PromptTemplate.parse("sample.txt", TEMPLATE)
                .render(Map.of("language", "Java", "projectName", "shop",
                        "sourceCode", "String s = \"{{language}} $1 \\\\\";"));

        // El valor se inserta literal: ni {{language}} se sustituye ni $1 se interpreta como grupo
        assertThat(rendered.user()).contains("String s = \"{{language}} $1 \\\\\";");
    }

    @Test
    void failsWhenAVariableIsMissing() {
        PromptTemplate template = PromptTemplate.parse("sample.txt", TEMPLATE);

        assertThatThrownBy(() -> template.render(Map.of("language", "Java")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projectName");
    }

    @Test
    void rejectsTemplatesWithoutVersionOrRequiredSections() {
        assertThatThrownBy(() -> PromptTemplate.parse("x.txt", TEMPLATE.replace("# version: 2.1.0\n", "")))
                .hasMessageContaining("version");
        assertThatThrownBy(() -> PromptTemplate.parse("x.txt", TEMPLATE.replace("=== OUTPUT FORMAT ===\nJSON\n", "")))
                .hasMessageContaining("OUTPUT FORMAT");
    }

    @Test
    void rejectsDuplicatedSectionsAndTextBeforeTheFirstSection() {
        assertThatThrownBy(() -> PromptTemplate.parse("x.txt", TEMPLATE + "=== TASK ===\nagain\n"))
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> PromptTemplate.parse("x.txt", "hello\n" + TEMPLATE))
                .hasMessageContaining("unexpected text");
    }
}
