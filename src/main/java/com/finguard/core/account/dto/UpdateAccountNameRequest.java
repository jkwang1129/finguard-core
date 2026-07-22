package com.finguard.core.account.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountNameRequest(
        @NotBlank(message = "accountName must not be blank")
        String accountName
) {
}
