package com.finguard.core.risk.rule;

import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;

import java.math.BigDecimal;
import java.util.Objects;

public record RiskRuleResult(
        RiskRuleCode ruleCode,
        RiskReasonCode reasonCode,
        BigDecimal observedAmount,
        BigDecimal thresholdAmount,
        Integer observedCount,
        Integer thresholdCount,
        Integer windowSeconds,
        String reasonSummary
) {

    private static final int MAX_SUMMARY_LENGTH = 255;

    public RiskRuleResult {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        if (!reasonCode.belongsTo(ruleCode)) {
            throw new IllegalArgumentException(
                    "reasonCode must belong to ruleCode"
            );
        }
        reasonSummary = normalizeSummary(reasonSummary);
        validateShape(
                ruleCode,
                observedAmount,
                thresholdAmount,
                observedCount,
                thresholdCount,
                windowSeconds
        );
    }

    public static RiskRuleResult largeAmount(
            BigDecimal observedAmount,
            BigDecimal thresholdAmount,
            String reasonSummary) {
        return new RiskRuleResult(
                RiskRuleCode.LARGE_AMOUNT,
                RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD,
                observedAmount,
                thresholdAmount,
                null,
                null,
                null,
                reasonSummary
        );
    }

    public static RiskRuleResult possibleDuplicate(
            int observedCount,
            int windowSeconds,
            String reasonSummary) {
        return new RiskRuleResult(
                RiskRuleCode.POSSIBLE_DUPLICATE,
                RiskReasonCode.SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME,
                null,
                null,
                observedCount,
                1,
                windowSeconds,
                reasonSummary
        );
    }

    public static RiskRuleResult frequentTransaction(
            int observedCount,
            int thresholdCount,
            int windowSeconds,
            String reasonSummary) {
        return new RiskRuleResult(
                RiskRuleCode.FREQUENT_TRANSACTION,
                RiskReasonCode.EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD,
                null,
                null,
                observedCount,
                thresholdCount,
                windowSeconds,
                reasonSummary
        );
    }

    private static void validateShape(
            RiskRuleCode ruleCode,
            BigDecimal observedAmount,
            BigDecimal thresholdAmount,
            Integer observedCount,
            Integer thresholdCount,
            Integer windowSeconds) {
        switch (ruleCode) {
            case LARGE_AMOUNT -> validateLargeAmount(
                    observedAmount,
                    thresholdAmount,
                    observedCount,
                    thresholdCount,
                    windowSeconds
            );
            case POSSIBLE_DUPLICATE -> validateCountRule(
                    observedAmount,
                    thresholdAmount,
                    observedCount,
                    thresholdCount,
                    windowSeconds,
                    true
            );
            case FREQUENT_TRANSACTION -> validateCountRule(
                    observedAmount,
                    thresholdAmount,
                    observedCount,
                    thresholdCount,
                    windowSeconds,
                    false
            );
        }
    }

    private static void validateLargeAmount(
            BigDecimal observedAmount,
            BigDecimal thresholdAmount,
            Integer observedCount,
            Integer thresholdCount,
            Integer windowSeconds) {
        requirePositiveAmount(observedAmount, "observedAmount");
        requirePositiveAmount(thresholdAmount, "thresholdAmount");
        if (observedAmount.compareTo(thresholdAmount) < 0) {
            throw new IllegalArgumentException(
                    "observedAmount must reach thresholdAmount"
            );
        }
        if (observedCount != null
                || thresholdCount != null
                || windowSeconds != null) {
            throw new IllegalArgumentException(
                    "large amount result must not contain count values"
            );
        }
    }

    private static void validateCountRule(
            BigDecimal observedAmount,
            BigDecimal thresholdAmount,
            Integer observedCount,
            Integer thresholdCount,
            Integer windowSeconds,
            boolean fixedThresholdOne) {
        if (observedAmount != null || thresholdAmount != null) {
            throw new IllegalArgumentException(
                    "count result must not contain amount values"
            );
        }
        requirePositiveInteger(observedCount, "observedCount");
        requirePositiveInteger(thresholdCount, "thresholdCount");
        requirePositiveInteger(windowSeconds, "windowSeconds");
        if (fixedThresholdOne && thresholdCount != 1) {
            throw new IllegalArgumentException(
                    "possible duplicate thresholdCount must be 1"
            );
        }
        if (observedCount < thresholdCount) {
            throw new IllegalArgumentException(
                    "observedCount must reach thresholdCount"
            );
        }
    }

    private static void requirePositiveAmount(
            BigDecimal amount,
            String fieldName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                    fieldName + " scale must not exceed 2"
            );
        }
    }

    private static void requirePositiveInteger(
            Integer value,
            String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }

    private static String normalizeSummary(String reasonSummary) {
        if (reasonSummary == null) {
            throw new IllegalArgumentException(
                    "reasonSummary must not be null"
            );
        }
        String normalized = reasonSummary.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "reasonSummary must not be blank"
            );
        }
        if (normalized.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException(
                    "reasonSummary must not exceed 255 characters"
            );
        }
        return normalized;
    }
}
