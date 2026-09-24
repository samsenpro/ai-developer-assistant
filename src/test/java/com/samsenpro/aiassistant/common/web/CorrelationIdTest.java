package com.samsenpro.aiassistant.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @Test
    void keepsValidIdsFromTheClient() {
        assertThat(CorrelationId.resolve("order-123:abc.DEF_9")).isEqualTo("order-123:abc.DEF_9");
    }

    @Test
    void replacesMissingOrUnsafeIdsWithAGeneratedOne() {
        assertThat(CorrelationId.resolve(null)).hasSize(36);
        // Un valor con saltos de línea permitiría falsificar líneas de log
        assertThat(CorrelationId.resolve("abc\nFAKE LOG LINE")).hasSize(36).doesNotContain("FAKE");
        assertThat(CorrelationId.resolve("x".repeat(200))).hasSize(36);
    }
}
