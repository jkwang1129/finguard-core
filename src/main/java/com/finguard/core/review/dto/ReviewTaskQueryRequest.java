package com.finguard.core.review.dto;

import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.risk.model.RiskRuleCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewTaskQueryRequest(
        @Min(value = 1, message = "page must be at least 1")
        Long page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Long size,

        ReviewTaskStatus status,

        ReviewTaskSourceType sourceType,

        ReconciliationResultType resultType,

        RiskRuleCode ruleCode
) {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_SIZE = 20L;

    public ReviewTaskQueryRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
}
