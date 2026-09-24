package com.samsenpro.aiassistant.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    Optional<AnalysisJob> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    long countByUserIdAndStatusIn(Long userId, Collection<JobStatus> statuses);

    Page<AnalysisJob> findByUserId(Long userId, Pageable pageable);

    List<AnalysisJob> findByStatusOrderByCreatedAt(JobStatus status);

    /**
     * Reclama el job de forma atómica (PENDING → PROCESSING). Si otro hilo ya lo reclamó, no
     * actualiza nada: un job nunca se ejecuta dos veces.
     */
    @Modifying
    @Transactional
    @Query("""
            update AnalysisJob j set j.status = com.samsenpro.aiassistant.job.JobStatus.PROCESSING, j.startedAt = :now
            where j.id = :id and j.status = com.samsenpro.aiassistant.job.JobStatus.PENDING""")
    int claim(UUID id, Instant now);
}
