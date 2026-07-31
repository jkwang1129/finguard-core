package com.finguard.core.transaction.model;

import java.util.Objects;

public record TransactionBusinessKey(
        Long accountId,
        String externalTransactionNo) {

    public TransactionBusinessKey {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "accountId must be positive"
            );
        }
        Objects.requireNonNull(
                externalTransactionNo,
                "externalTransactionNo must not be null"
        );
        if (externalTransactionNo.isBlank()) {
            throw new IllegalArgumentException(
                    "externalTransactionNo must not be blank"
            );
        }
    }
}
