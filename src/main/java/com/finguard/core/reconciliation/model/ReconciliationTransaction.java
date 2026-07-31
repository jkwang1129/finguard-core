package com.finguard.core.reconciliation.model;

import com.finguard.core.transaction.model.TransactionDirection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record ReconciliationTransaction(
        Long id,
        Long accountId,
        String externalTransactionNo,
        TransactionDirection direction,
        BigDecimal amount,
        LocalDateTime transactionTime
) {

    public ReconciliationTransaction {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "accountId must be positive"
            );
        }
        if (externalTransactionNo == null
                || externalTransactionNo.isBlank()) {
            throw new IllegalArgumentException(
                    "externalTransactionNo must not be blank"
            );
        }
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(
                transactionTime,
                "transactionTime must not be null"
        );
    }
}
