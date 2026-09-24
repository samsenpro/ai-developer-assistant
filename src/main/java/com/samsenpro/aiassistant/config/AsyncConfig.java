package com.samsenpro.aiassistant.config;

import com.samsenpro.aiassistant.job.JobProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
public class AsyncConfig {

    public static final String JOB_EXECUTOR = "aiJobExecutor";

    @Bean(name = JOB_EXECUTOR)
    ThreadPoolTaskExecutor aiJobExecutor(JobProperties properties, MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-job-");
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setTaskDecorator(mdcPropagation());
        // En un apagado ordenado se deja terminar a los jobs en curso (hasta 30 s)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        Gauge.builder("ai.jobs.queued", executor, e -> e.getThreadPoolExecutor().getQueue().size())
                .description("Jobs de análisis esperando un hilo libre")
                .register(registry);
        Gauge.builder("ai.jobs.running", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Jobs de análisis en ejecución")
                .register(registry);
        return executor;
    }

    /** Copia el MDC (correlationId) del hilo que encola al hilo del job. */
    private static TaskDecorator mdcPropagation() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}
