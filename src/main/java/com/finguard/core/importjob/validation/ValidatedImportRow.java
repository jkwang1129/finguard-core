package com.finguard.core.importjob.validation;

import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record ValidatedImportRow(
        int rowNumber,
        Long accountId,
        String externalTransactionNo,
        TransactionDirection direction,
        BigDecimal amount,
        LocalDateTime transactionTime,
        String description,
        TransactionSource source) {

    public ValidatedImportRow {
        if (rowNumber < 2) {
            throw new IllegalArgumentException(
                    "rowNumber must identify a data record"
            );
        }
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "accountId must be positive"
            );
        }
        Objects.requireNonNull(
                externalTransactionNo,
                "externalTransactionNo must not be null"
        );
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(
                transactionTime,
                "transactionTime must not be null"
        );
        if (externalTransactionNo.isBlank()) {
            throw new IllegalArgumentException(
                    "externalTransactionNo must not be blank"
            );
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() != 2) {
            throw new IllegalArgumentException(
                    "amount must be positive with scale 2"
            );
        }
        if (source != TransactionSource.CSV_IMPORT) {
            throw new IllegalArgumentException(
                    "validated import rows must use CSV_IMPORT"
            );
        }
    }
}
