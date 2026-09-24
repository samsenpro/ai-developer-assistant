package com.samsenpro.aiassistant.usage;

import com.samsenpro.aiassistant.ai.AIOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AIUsageRepository extends JpaRepository<AIUsage, Long> {

    @Query("select coalesce(sum(u.totalTokens), 0) from AIUsage u where u.userId = :userId and u.createdAt >= :since")
    long sumTokensSince(Long userId, Instant since);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant since);

    long countByUserIdAndOperationAndCreatedAtGreaterThanEqual(Long userId, AIOperation operation, Instant since);

    @Query("""
            select new com.samsenpro.aiassistant.usage.OperationUsage$Row(
                u.operation,
                count(u),
                sum(case when u.status = com.samsenpro.aiassistant.usage.UsageStatus.SUCCESS then 0 else 1 end),
                sum(u.inputTokens),
                sum(u.outputTokens),
                sum(u.totalTokens),
                avg(u.durationMs))
            from AIUsage u
            where u.userId = :userId and u.createdAt >= :from and u.createdAt < :to
            group by u.operation
            order by u.operation""")
    List<OperationUsage.Row> summarizeByOperation(Long userId, Instant from, Instant to);

    Page<AIUsage> findByUserId(Long userId, Pageable pageable);
}
