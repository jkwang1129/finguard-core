package com.finguard.core.audit.service;

import com.finguard.core.audit.dto.AuditLogQueryRequest;
import com.finguard.core.audit.model.ReconciliationAuditCounts;
import com.finguard.core.audit.vo.AuditLogResponse;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.review.model.ReviewDecision;

public interface AuditLogService {

    void recordCsvUploadAccepted(Long importJobId, Long initiatedBy);

    void recordImportFailed(Long importJobId, Long initiatedBy);

    void recordReconciliationCompleted(
            Long reconciliationJobId,
            Long initiatedBy,
            ReconciliationAuditCounts counts
    );

    void recordReviewDecision(
            Long reviewTaskId,
            Long reviewerId,
            ReviewDecision decision
    );

    PageResponse<AuditLogResponse> query(AuditLogQueryRequest request);
}
