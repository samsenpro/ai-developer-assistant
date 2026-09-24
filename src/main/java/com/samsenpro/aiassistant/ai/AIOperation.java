package com.samsenpro.aiassistant.ai;

import java.util.Locale;

/** Operaciones de IA. El nombre en minúsculas es el valor del tag {@code operation} de las métricas. */
public enum AIOperation {

    EXPLAIN,
    REVIEW,
    IMPROVE,
    GENERATE_TESTS,
    GENERATE_DOCUMENTATION,
    ANALYZE_ERROR,
    CHAT;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
