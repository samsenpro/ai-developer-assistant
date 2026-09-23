package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.ai.AIOperation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * @param cacheTtl               antigüedad máxima de un resultado para reutilizarlo en lugar de llamar al LLM
 * @param defaultMaxOutputTokens límite de tokens de salida por defecto
 * @param maxOutputTokens        límite de tokens de salida por operación
 */
@Validated
@ConfigurationProperties(prefix = "ai.analysis")
public record AnalysisProperties(@NotNull Duration cacheTtl, @Positive int defaultMaxOutputTokens,
                                 Map<AIOperation, Integer> maxOutputTokens) {

    public int maxOutputTokens(AIOperation operation) {
        return maxOutputTokens == null ? defaultMaxOutputTokens
                : maxOutputTokens.getOrDefault(operation, defaultMaxOutputTokens);
    }
}
