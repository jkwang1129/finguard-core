package com.finguard.core.risk.rule;

import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.risk.config.RiskProperties;
import com.finguard.core.risk.model.RiskRuleCode;
import com.finguard.core.transaction.model.TransactionDirection;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Component
public class FrequentTransactionRule implements RiskRule {

    private static final String REASON_SUMMARY =
            "Expense count reached configured threshold in time window";

    private final RiskProperties.FrequentTransaction properties;

    public FrequentTransactionRule(RiskProperties riskProperties) {
        this.properties = Objects.requireNonNull(
                riskProperties,
                "riskProperties must not be null"
        ).getFrequentTransaction();
    }

    @Override
    public RiskRuleCode ruleCode() {
        return RiskRuleCode.FREQUENT_TRANSACTION;
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public Optional<RiskRuleResult> evaluate(
            RiskEvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (!enabled()
                || context.currentTransaction().direction()
                != TransactionDirection.EXPENSE) {
            return Optional.empty();
        }
        ReconciliationTransaction current = context.currentTransaction();
        LocalDateTime windowStart = current.transactionTime()
                .minus(properties.getWindow());
        int observed = (int) context.historicalTransactions().stream()
                .filter(candidate -> candidate.direction()
                        == TransactionDirection.EXPENSE)
                .filter(candidate -> !candidate.transactionTime()
                        .isBefore(windowStart))
                .filter(candidate -> compare(candidate, current) <= 0)
                .count();
        if (observed < properties.getThresholdCount()) {
            return Optional.empty();
        }
        return Optional.of(RiskRuleResult.frequentTransaction(
                observed,
                properties.getThresholdCount(),
                properties.windowSeconds(),
                REASON_SUMMARY
        ));
    }

    private int compare(
            ReconciliationTransaction left,
            ReconciliationTransaction right) {
        int timeComparison = left.transactionTime()
                .compareTo(right.transactionTime());
        return timeComparison != 0
                ? timeComparison
                : left.id().compareTo(right.id());
    }
}
