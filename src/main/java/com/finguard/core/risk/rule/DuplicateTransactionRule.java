package com.finguard.core.risk.rule;

import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.risk.config.RiskProperties;
import com.finguard.core.risk.model.RiskRuleCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Component
public class DuplicateTransactionRule implements RiskRule {

    private static final String REASON_SUMMARY =
            "Earlier same-account direction and amount transaction found"
                    + " in configured time window";

    private final RiskProperties.PossibleDuplicate properties;

    public DuplicateTransactionRule(RiskProperties riskProperties) {
        this.properties = Objects.requireNonNull(
                riskProperties,
                "riskProperties must not be null"
        ).getPossibleDuplicate();
    }

    @Override
    public RiskRuleCode ruleCode() {
        return RiskRuleCode.POSSIBLE_DUPLICATE;
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public Optional<RiskRuleResult> evaluate(
            RiskEvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (!enabled()) {
            return Optional.empty();
        }
        ReconciliationTransaction current = context.currentTransaction();
        LocalDateTime windowStart = current.transactionTime()
                .minus(properties.getWindow());
        int observed = (int) context.historicalTransactions().stream()
                .filter(candidate -> isEarlierCandidate(
                        candidate,
                        current,
                        windowStart
                ))
                .count();
        if (observed == 0) {
            return Optional.empty();
        }
        return Optional.of(RiskRuleResult.possibleDuplicate(
                observed,
                properties.windowSeconds(),
                REASON_SUMMARY
        ));
    }

    private boolean isEarlierCandidate(
            ReconciliationTransaction candidate,
            ReconciliationTransaction current,
            LocalDateTime windowStart) {
        return candidate.accountId().equals(current.accountId())
                && candidate.direction() == current.direction()
                && candidate.amount().compareTo(current.amount()) == 0
                && !candidate.transactionTime().isBefore(windowStart)
                && compare(candidate, current) < 0;
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
