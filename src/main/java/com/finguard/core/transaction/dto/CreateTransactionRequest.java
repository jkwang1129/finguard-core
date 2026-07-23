package com.finguard.core.transaction.dto;

import com.finguard.core.transaction.model.TransactionDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTransactionRequest(
        @NotNull(message = "accountId must not be null")
        @Positive(message = "accountId must be positive")
        Long accountId,

        @NotBlank(message = "externalTransactionNo must not be blank")
        @Size(max = 128, message = "externalTransactionNo must not exceed 128 characters")
        String externalTransactionNo,

        @NotNull(message = "direction must not be null")
        TransactionDirection direction,

        @NotNull(message = "amount must not be null")
        @DecimalMin(
                value = "0.00",
                inclusive = false,
                message = "amount must be greater than zero"
        )
        @Digits(
                integer = 17,
                fraction = 2,
                message = "amount must have at most 17 integer digits and 2 decimal places"
        )
        BigDecimal amount,

        @NotNull(message = "transactionTime must not be null")
        LocalDateTime transactionTime,

        @Size(max = 255, message = "description must not exceed 255 characters")
        String description
) {
}
