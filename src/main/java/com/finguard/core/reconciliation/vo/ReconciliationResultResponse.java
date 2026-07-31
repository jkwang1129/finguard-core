package com.finguard.core.reconciliation.vo;

import com.finguard.core.reconciliation.model.ReconciliationMatchMethod;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;

import java.time.LocalDateTime;

public record ReconciliationResultResponse(
        Long id,
        Long reconciliationJobId,
        Long csvTransactionId,
        Long manualTransactionId,
        ReconciliationResultType resultType,
        ReconciliationMatchMethod matchMethod,
        ReconciliationReasonCode reasonCode,
        LocalDateTime createdAt
) {
}
