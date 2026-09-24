package com.samsenpro.aiassistant.ai.analysis.handler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedCodeTest {

    @Test
    void stripsLineNumbersCopiedFromThePrompt() {
        String numbered = "  1 | package a;\n  2 | \n  3 | class A {}\n";

        assertThat(GeneratedCode.stripLineNumbers(numbered)).isEqualTo("package a;\n\nclass A {}\n");
    }

    @Test
    void leavesCodeWithoutLineNumbersUntouched() {
        String code = "int total = a | b;\nString s = \"1 | 2\";\n";

        assertThat(GeneratedCode.stripLineNumbers(code)).isEqualTo(code);
    }

    @Test
    void resolvesPathsOnlyWhenTheMatchIsUnambiguous() {
        Map<String, Integer> known = Map.of("src/a/User.java", 10, "src/b/User.java", 10, "src/Order.java", 5);

        assertThat(GeneratedCode.resolvePath("src/Order.java", known)).contains("src/Order.java");
        assertThat(GeneratedCode.resolvePath("Order.java", known)).contains("src/Order.java");
        assertThat(GeneratedCode.resolvePath("src\\Order.java", known)).contains("src/Order.java");
        assertThat(GeneratedCode.resolvePath("User.java", known)).isEmpty();
        assertThat(GeneratedCode.resolvePath("Missing.java", known)).isEmpty();
    }
}
