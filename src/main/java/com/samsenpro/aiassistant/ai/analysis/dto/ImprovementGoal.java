package com.samsenpro.aiassistant.ai.analysis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.util.Locale;

public enum ImprovementGoal {
    PERFORMANCE,
    READABILITY,
    CLEAN_CODE,
    SECURITY,
    ARCHITECTURE,
    @JsonEnumDefaultValue
    OTHER;

    /** Acepta también la forma en minúsculas con guion: {@code clean-code}. */
    @JsonCreator
    public static ImprovementGoal from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
