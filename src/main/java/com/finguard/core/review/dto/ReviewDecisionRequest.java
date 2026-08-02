package com.finguard.core.review.dto;

import com.finguard.core.review.model.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReviewDecisionRequest(
        @NotNull(message = "decision must not be null")
        ReviewDecision decision,

        @NotNull(message = "version must not be null")
        @PositiveOrZero(message = "version must not be negative")
        Integer version,

        String note
) {
}
