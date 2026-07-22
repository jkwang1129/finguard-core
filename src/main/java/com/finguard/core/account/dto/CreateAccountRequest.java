package com.finguard.core.account.dto;

import com.finguard.core.account.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotBlank(message = "accountNo must not be blank")
        String accountNo,

        @NotBlank(message = "accountName must not be blank")
        String accountName,

        @NotNull(message = "accountType must not be null")
        AccountType accountType
) {
}
