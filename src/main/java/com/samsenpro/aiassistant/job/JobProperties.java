package com.samsenpro.aiassistant.job;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pool de ejecución de jobs. Es pequeño a propósito: cada job es una llamada larga al LLM, y la
 * concurrencia real la limita el proveedor (rate limits), no la CPU.
 *
 * @param queueCapacity jobs en espera; si la cola está llena, el job se marca como FAILED
 */
@Validated
@ConfigurationProperties(prefix = "ai.jobs")
public record JobProperties(@Positive int corePoolSize, @Positive int maxPoolSize, @Positive int queueCapacity) {
}
