package com.finguard.core.review.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReviewTaskContextResponse(
        ReviewTask reviewTask,
        Transaction transaction,
        Reconciliation reconciliation,
        List<RiskFact> riskFacts,
        AccountSummary accountSummary,
        Instant capturedAt
) {

    public record ReviewTask(
            long id,
            long version,
            String status,
            String ruleCode,
            String reasonCode,
            Instant createdAt) {
    }

    public record Transaction(
            long id,
            long accountId,
            BigDecimal amount,
            String currency,
            Instant occurredAt,
            String sourceType) {
    }

    public record Reconciliation(
            String resultType,
            String matchMethod,
            String reasonCode) {
    }

    public record RiskFact(
            String code,
            String value,
            String source) {
    }

    public record AccountSummary(
            String status,
            String displayName) {
    }
}
