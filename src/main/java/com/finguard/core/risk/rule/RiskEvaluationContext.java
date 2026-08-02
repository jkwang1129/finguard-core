package com.finguard.core.risk.rule;

import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public record RiskEvaluationContext(
        Long reconciliationJobId,
        Long reconciliationResultId,
        ReconciliationTransaction currentTransaction,
        ReconciliationResultType reconciliationResultType,
        ReconciliationReasonCode reconciliationReasonCode,
        List<ReconciliationTransaction> historicalTransactions,
        ZoneId businessZoneId
) {

    public RiskEvaluationContext {
        requirePositive(reconciliationJobId, "reconciliationJobId");
        requirePositive(
                reconciliationResultId,
                "reconciliationResultId"
        );
        Objects.requireNonNull(
                currentTransaction,
                "currentTransaction must not be null"
        );
        Objects.requireNonNull(
                reconciliationResultType,
                "reconciliationResultType must not be null"
        );
        Objects.requireNonNull(
                reconciliationReasonCode,
                "reconciliationReasonCode must not be null"
        );
        Objects.requireNonNull(
                historicalTransactions,
                "historicalTransactions must not be null"
        );
        historicalTransactions = List.copyOf(historicalTransactions);
        if (historicalTransactions.stream().anyMatch(
                candidate -> candidate.accountId() == null
                        || !candidate.accountId().equals(
                        currentTransaction.accountId()
                )
        )) {
            throw new IllegalArgumentException(
                    "historicalTransactions must belong to current account"
            );
        }
        Objects.requireNonNull(
                businessZoneId,
                "businessZoneId must not be null"
        );
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}
