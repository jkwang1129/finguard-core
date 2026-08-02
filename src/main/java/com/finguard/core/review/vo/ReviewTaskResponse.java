package com.finguard.core.review.vo;

import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;

import java.time.LocalDateTime;

public record ReviewTaskResponse(
        Long id,
        ReviewTaskSourceType sourceType,
        Long reconciliationResultId,
        Long riskHitId,
        Long csvTransactionId,
        ReconciliationResultType resultType,
        RiskRuleCode ruleCode,
        RiskReasonCode reasonCode,
        ReviewTaskStatus status,
        Integer version,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String decisionNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
