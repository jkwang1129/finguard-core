package com.finguard.core.risk.model;

public enum RiskRuleCode {
    LARGE_AMOUNT(10),
    POSSIBLE_DUPLICATE(20),
    FREQUENT_TRANSACTION(30);

    private final int executionOrder;

    RiskRuleCode(int executionOrder) {
        this.executionOrder = executionOrder;
    }

    public int getExecutionOrder() {
        return executionOrder;
    }
}
