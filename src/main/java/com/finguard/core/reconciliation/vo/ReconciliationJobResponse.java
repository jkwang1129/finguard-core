package com.finguard.core.reconciliation.vo;

import com.finguard.core.reconciliation.model.ReconciliationJobStatus;

import java.time.LocalDateTime;

public record ReconciliationJobResponse(
        Long id,
        Long importJobId,
        ReconciliationJobStatus status,
        Integer totalCount,
        Integer matchedCount,
        Integer unmatchedCount,
        Integer duplicateCount,
        Integer suspiciousCount,
        String errorSummary,
        Long createdBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean duplicateRequest
) {
}
