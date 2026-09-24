package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Métricas de IA. Todos los tags son de baja cardinalidad (valores de enums o nombres de proveedor
 * configurados): nunca userId, projectId ni conversationId, que crearían una serie temporal por
 * usuario en Prometheus.
 * <p>
 * Nombres en Prometheus: ai_requests_total, ai_requests_failed_total, ai_request_duration_seconds,
 * ai_tokens_used_total, ai_cache_requests_total, ai_rate_limited_total,
 * ai_prompt_injection_suspected_total y ai_jobs_total.
 */
@Component
public class AIMetrics {

    private final MeterRegistry registry;

    public AIMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordCall(AIOperation operation, String provider, UsageStatus status, Duration duration,
                           int inputTokens, int outputTokens) {
        Counter.builder("ai.requests")
                .description("Llamadas al proveedor LLM")
                .tag("operation", operation.tag()).tag("provider", provider).tag("status", status.tag())
                .register(registry).increment();
        Timer.builder("ai.request.duration")
                .description("Duración de las llamadas al proveedor LLM")
                .tag("operation", operation.tag()).tag("provider", provider).tag("status", status.tag())
                .publishPercentileHistogram()
                .register(registry).record(duration);
        if (inputTokens > 0) {
            tokens(operation, provider, "input").increment(inputTokens);
        }
        if (outputTokens > 0) {
            tokens(operation, provider, "output").increment(outputTokens);
        }
    }

    public void recordFailure(AIOperation operation, String provider, ErrorCode error) {
        Counter.builder("ai.requests.failed")
                .description("Llamadas al proveedor LLM fallidas, por tipo de error")
                .tag("operation", operation.tag()).tag("provider", provider)
                .tag("status", UsageStatus.FAILED.tag())
                .tag("error", error.name().toLowerCase(Locale.ROOT))
                .register(registry).increment();
    }

    public void recordCacheLookup(AIOperation operation, boolean hit) {
        Counter.builder("ai.cache.requests")
                .description("Consultas a la caché de resultados de IA")
                .tag("operation", operation.tag()).tag("result", hit ? "hit" : "miss")
                .register(registry).increment();
    }

    public void recordRateLimited(AIOperation operation, String reason) {
        Counter.builder("ai.rate.limited")
                .description("Peticiones de IA rechazadas por límites de uso")
                .tag("operation", operation.tag()).tag("reason", reason.toLowerCase(Locale.ROOT))
                .register(registry).increment();
    }

    public void recordPromptInjectionSuspected(AIOperation operation) {
        Counter.builder("ai.prompt.injection.suspected")
                .description("Contenido analizado con texto que parece instrucciones para el modelo")
                .tag("operation", operation.tag())
                .register(registry).increment();
    }

    public void recordJob(AIOperation operation, String status) {
        Counter.builder("ai.jobs")
                .description("Jobs de análisis que terminan, por estado final")
                .tag("operation", operation.tag()).tag("status", status.toLowerCase(Locale.ROOT))
                .register(registry).increment();
    }

    private Counter tokens(AIOperation operation, String provider, String type) {
        return Counter.builder("ai.tokens.used")
                .description("Tokens consumidos en el proveedor LLM")
                .tag("operation", operation.tag()).tag("provider", provider).tag("type", type)
                .register(registry);
    }
}
