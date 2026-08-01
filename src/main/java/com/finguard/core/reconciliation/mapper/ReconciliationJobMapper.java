package com.finguard.core.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.reconciliation.entity.ReconciliationJob;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ReconciliationJobMapper
        extends BaseMapper<ReconciliationJob> {

    @Select("""
            SELECT id,
                   import_job_id AS importJobId,
                   status,
                   total_count AS totalCount,
                   matched_count AS matchedCount,
                   unmatched_count AS unmatchedCount,
                   duplicate_count AS duplicateCount,
                   suspicious_count AS suspiciousCount,
                   error_summary AS errorSummary,
                   created_by AS createdBy,
                   started_at AS startedAt,
                   finished_at AS finishedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM reconciliation_jobs
            WHERE id = #{reconciliationJobId}
            FOR UPDATE
            """)
    ReconciliationJob findByIdForUpdate(
            @Param("reconciliationJobId") Long reconciliationJobId
    );

    @Select("""
            SELECT id,
                   import_job_id AS importJobId,
                   status,
                   total_count AS totalCount,
                   matched_count AS matchedCount,
                   unmatched_count AS unmatchedCount,
                   duplicate_count AS duplicateCount,
                   suspicious_count AS suspiciousCount,
                   error_summary AS errorSummary,
                   created_by AS createdBy,
                   started_at AS startedAt,
                   finished_at AS finishedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM reconciliation_jobs
            WHERE import_job_id = #{importJobId}
            """)
    ReconciliationJob findByImportJobId(
            @Param("importJobId") Long importJobId
    );

    @Update("""
            UPDATE reconciliation_jobs
            SET status = 'PROCESSING',
                started_at = #{startedAt}
            WHERE id = #{reconciliationJobId}
              AND status = 'PENDING'
            """)
    int markProcessing(
            @Param("reconciliationJobId") Long reconciliationJobId,
            @Param("startedAt") LocalDateTime startedAt
    );

    @Update("""
            UPDATE reconciliation_jobs
            SET status = #{status},
                total_count = #{totalCount},
                matched_count = #{matchedCount},
                unmatched_count = #{unmatchedCount},
                duplicate_count = #{duplicateCount},
                suspicious_count = #{suspiciousCount},
                error_summary = NULL,
                finished_at = #{finishedAt}
            WHERE id = #{reconciliationJobId}
              AND status = 'PROCESSING'
            """)
    int completeProcessing(
            @Param("reconciliationJobId") Long reconciliationJobId,
            @Param("status") ReconciliationJobStatus status,
            @Param("totalCount") int totalCount,
            @Param("matchedCount") int matchedCount,
            @Param("unmatchedCount") int unmatchedCount,
            @Param("duplicateCount") int duplicateCount,
            @Param("suspiciousCount") int suspiciousCount,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    @Update("""
            UPDATE reconciliation_jobs
            SET status = 'FAILED',
                total_count = 0,
                matched_count = 0,
                unmatched_count = 0,
                duplicate_count = 0,
                suspicious_count = 0,
                error_summary = #{errorSummary},
                started_at = COALESCE(started_at, #{finishedAt}),
                finished_at = #{finishedAt}
            WHERE id = #{reconciliationJobId}
              AND status = 'PROCESSING'
            """)
    int markFailed(
            @Param("reconciliationJobId") Long reconciliationJobId,
            @Param("errorSummary") String errorSummary,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    @Update("""
            UPDATE reconciliation_jobs
            SET status = 'FAILED',
                total_count = 0,
                matched_count = 0,
                unmatched_count = 0,
                duplicate_count = 0,
                suspicious_count = 0,
                error_summary = #{errorSummary},
                started_at = COALESCE(started_at, #{finishedAt}),
                finished_at = #{finishedAt}
            WHERE id = #{reconciliationJobId}
              AND status IN ('PENDING', 'PROCESSING')
            """)
    int failNonTerminal(
            @Param("reconciliationJobId") Long reconciliationJobId,
            @Param("errorSummary") String errorSummary,
            @Param("finishedAt") LocalDateTime finishedAt
    );
}
