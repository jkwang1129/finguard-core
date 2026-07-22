package com.finguard.core.account.dto;

import com.finguard.core.account.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        @NotNull(message = "status must not be null")
        AccountStatus status
) {
}
