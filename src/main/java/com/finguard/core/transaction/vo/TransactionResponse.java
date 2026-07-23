package com.finguard.core.transaction.vo;

import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long accountId,
        String externalTransactionNo,
        TransactionDirection direction,
        BigDecimal amount,
        LocalDateTime transactionTime,
        String description,
        TransactionSource source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
