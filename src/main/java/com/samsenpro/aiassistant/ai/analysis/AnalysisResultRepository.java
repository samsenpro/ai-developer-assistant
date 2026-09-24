package com.samsenpro.aiassistant.ai.analysis;

import com.samsenpro.aiassistant.ai.AIOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Resultado original más reciente con esa entrada exacta (nivel 2 de la caché). */
    @Query("""
            select a from AnalysisResult a
            where a.userId = :userId and a.inputHash = :inputHash
              and a.status = com.samsenpro.aiassistant.ai.analysis.AnalysisStatus.COMPLETED
              and a.cached = false and a.completedAt >= :since
            order by a.completedAt desc
            limit 1""")
    Optional<AnalysisResult> findReusable(Long userId, String inputHash, Instant since);

    Page<AnalysisResult> findByProjectId(Long projectId, Pageable pageable);

    Page<AnalysisResult> findByProjectIdAndOperation(Long projectId, AIOperation operation, Pageable pageable);
}
