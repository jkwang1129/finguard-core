package com.finguard.core.reconciliation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReconciliationJobRequest(
        @NotNull(message = "importJobId is required")
        @Positive(message = "importJobId must be positive")
        Long importJobId
) {
}
