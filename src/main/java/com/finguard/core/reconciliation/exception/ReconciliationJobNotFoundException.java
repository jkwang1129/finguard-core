package com.finguard.core.reconciliation.exception;

public class ReconciliationJobNotFoundException
        extends RuntimeException {

    public ReconciliationJobNotFoundException(
            Long reconciliationJobId) {
        super(
                "Reconciliation job was not found: "
                        + reconciliationJobId
        );
    }
}
