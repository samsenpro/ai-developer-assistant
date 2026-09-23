package com.samsenpro.aiassistant.usage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.samsenpro.aiassistant.ai.AIOperation;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Token bucket por usuario y operación. Protege frente a ráfagas (un bucle en un cliente, un
 * doble clic repetido...) antes de gastar una sola llamada al LLM.
 * <p>
 * Los buckets viven en memoria de la instancia (acotados y con expiración). Con varias réplicas
 * habría que moverlos a un almacén compartido; ver "Production Considerations" en el README.
 */
@Component
public class AIRateLimiter {

    private final UsageLimitsProperties.Rate limits;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    public AIRateLimiter(UsageLimitsProperties properties) {
        this.limits = properties.rate();
    }

    /** Consume una petición; si no hay cupo devuelve cuánto hay que esperar. */
    public Optional<Duration> tryConsume(Long userId, AIOperation operation) {
        Bucket bucket = buckets.get(userId + ":" + operation, key -> newBucket(limits.forOperation(operation)));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Optional.empty() : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    public UsageLimitsProperties.RateLimit limitFor(AIOperation operation) {
        return limits.forOperation(operation);
    }

    private static Bucket newBucket(UsageLimitsProperties.RateLimit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.requests())
                        .refillGreedy(limit.requests(), limit.period())
                        .build())
                .build();
    }
}
