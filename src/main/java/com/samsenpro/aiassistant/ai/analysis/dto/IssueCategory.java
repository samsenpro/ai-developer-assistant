package com.samsenpro.aiassistant.ai.analysis.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum IssueCategory {
    BUG,
    CODE_SMELL,
    SECURITY,
    PERFORMANCE,
    MAINTAINABILITY,
    DUPLICATION,
    BAD_PRACTICE,
    /** Categoría que el modelo inventó: se conserva el hallazgo en lugar de descartar toda la revisión. */
    @JsonEnumDefaultValue
    OTHER
}
