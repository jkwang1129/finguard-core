package com.finguard.core.account.vo;

import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.account.model.AccountType;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNo,
        String accountName,
        AccountType accountType,
        String currency,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
