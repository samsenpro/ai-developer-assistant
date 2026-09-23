package com.samsenpro.aiassistant.ai.prompt;

/** Plantillas disponibles en {@code src/main/resources/prompts/}. */
public enum PromptId {

    EXPLAIN("explain.txt"),
    REVIEW("review.txt"),
    IMPROVE("improve.txt"),
    TESTS("tests.txt"),
    DOCUMENTATION("documentation.txt"),
    ERROR_ANALYSIS("error-analysis.txt"),
    CHAT("chat.txt");

    private final String file;

    PromptId(String file) {
        this.file = file;
    }

    public String file() {
        return file;
    }
}
