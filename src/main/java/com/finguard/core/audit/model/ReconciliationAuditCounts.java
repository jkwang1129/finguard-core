package com.finguard.core.audit.model;

public record ReconciliationAuditCounts(
        int total,
        int matched,
        int unmatched,
        int duplicate,
        int suspicious
) {

    public ReconciliationAuditCounts {
        if (total < 0
                || matched < 0
                || unmatched < 0
                || duplicate < 0
                || suspicious < 0) {
            throw new IllegalArgumentException(
                    "Reconciliation audit counts must not be negative"
            );
        }
        if (total != matched + unmatched + duplicate + suspicious) {
            throw new IllegalArgumentException(
                    "Reconciliation audit counts must be balanced"
            );
        }
    }
}
