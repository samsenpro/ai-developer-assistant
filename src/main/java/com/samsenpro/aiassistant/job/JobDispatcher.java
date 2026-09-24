package com.samsenpro.aiassistant.job;

import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.UUID;

/**
 * Encola los jobs en el pool de ejecución:
 * <ul>
 *     <li>al confirmarse la transacción que los crea (nunca antes: el hilo del job debe encontrarlos
 *     en la base de datos);</li>
 *     <li>al arrancar la aplicación, los que quedaron PENDING. Los que estaban PROCESSING cuando la
 *     aplicación se detuvo se marcan FAILED: no se sabe si la llamada al LLM llegó a hacerse, y el
 *     usuario puede reenviarlos.</li>
 * </ul>
 * Supone una sola instancia de la aplicación; ver "Production Considerations" en el README.
 */
@Component
public class JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);

    private final ThreadPoolTaskExecutor executor;
    private final JobRunner runner;
    private final AnalysisJobRepository repository;
    private final Clock clock;

    public JobDispatcher(@Qualifier(AsyncConfig.JOB_EXECUTOR) ThreadPoolTaskExecutor executor, JobRunner runner,
                         AnalysisJobRepository repository, Clock clock) {
        this.executor = executor;
        this.runner = runner;
        this.repository = repository;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onSubmitted(JobService.JobSubmittedEvent event) {
        dispatch(event.jobId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        var interrupted = repository.findByStatusOrderByCreatedAt(JobStatus.PROCESSING);
        interrupted.forEach(job -> {
            job.fail(ErrorCode.INTERNAL_ERROR.name(), "The job was interrupted by a server restart", clock.instant());
            repository.save(job);
        });
        var pending = repository.findByStatusOrderByCreatedAt(JobStatus.PENDING);
        pending.forEach(job -> dispatch(job.getId()));
        if (!interrupted.isEmpty() || !pending.isEmpty()) {
            log.info("Job recovery: {} interrupted job(s) marked as FAILED, {} pending job(s) re-queued",
                    interrupted.size(), pending.size());
        }
    }

    private void dispatch(UUID jobId) {
        try {
            executor.execute(() -> runner.run(jobId));
        } catch (TaskRejectedException ex) {
            log.warn("Job queue is full, job {} rejected", jobId);
            runner.reject(jobId);
        }
    }
}
