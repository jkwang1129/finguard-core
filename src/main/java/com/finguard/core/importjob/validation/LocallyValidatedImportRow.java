package com.finguard.core.importjob.validation;

import com.finguard.core.transaction.model.TransactionDirection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record LocallyValidatedImportRow(
        int rowNumber,
        String accountNo,
        String externalTransactionNo,
        TransactionDirection direction,
        BigDecimal amount,
        LocalDateTime transactionTime,
        String description) {
}
