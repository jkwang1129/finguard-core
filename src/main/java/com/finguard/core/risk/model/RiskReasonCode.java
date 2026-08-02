package com.finguard.core.risk.model;

public enum RiskReasonCode {
    AMOUNT_AT_OR_ABOVE_THRESHOLD(RiskRuleCode.LARGE_AMOUNT),
    SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME(
            RiskRuleCode.POSSIBLE_DUPLICATE
    ),
    EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD(
            RiskRuleCode.FREQUENT_TRANSACTION
    );

    private final RiskRuleCode ruleCode;

    RiskReasonCode(RiskRuleCode ruleCode) {
        this.ruleCode = ruleCode;
    }

    public RiskRuleCode getRuleCode() {
        return ruleCode;
    }

    public boolean belongsTo(RiskRuleCode candidateRuleCode) {
        return ruleCode == candidateRuleCode;
    }
}
