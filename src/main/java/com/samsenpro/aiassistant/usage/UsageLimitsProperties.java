package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Límites de uso de la IA. Todos se cambian por configuración (application.yml o variables de
 * entorno), sin tocar código.
 *
 * @param rate               ráfagas: peticiones por usuario y operación en una ventana (token bucket)
 * @param daily              cuotas diarias (día UTC) calculadas sobre ai_usage
 * @param maxPendingJobs     jobs PENDING/PROCESSING simultáneos por usuario
 */
@Validated
@ConfigurationProperties(prefix = "ai.limits")
public record UsageLimitsProperties(@Valid @NotNull Rate rate, @Valid @NotNull Daily daily,
                                    @Positive int maxPendingJobs) {

    /**
     * @param defaults   límite para las operaciones sin límite propio
     * @param operations límites por operación
     */
    public record Rate(@Valid @NotNull RateLimit defaults, Map<AIOperation, @Valid RateLimit> operations) {

        public RateLimit forOperation(AIOperation operation) {
            return operations == null ? defaults : operations.getOrDefault(operation, defaults);
        }
    }

    public record RateLimit(@Positive int requests, @NotNull Duration period) {
    }

    /**
     * @param tokensPerUser        tokens (entrada + salida) por usuario y día
     * @param requestsPerUser      llamadas al proveedor por usuario y día
     * @param requestsPerOperation límite diario adicional por operación (opcional)
     */
    public record Daily(@Min(1) long tokensPerUser, @Min(1) long requestsPerUser,
                        Map<AIOperation, Long> requestsPerOperation) {

        public Long forOperation(AIOperation operation) {
            return requestsPerOperation == null ? null : requestsPerOperation.get(operation);
        }
    }
}
