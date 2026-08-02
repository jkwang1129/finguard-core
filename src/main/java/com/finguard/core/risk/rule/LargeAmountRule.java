package com.finguard.core.risk.rule;

import com.finguard.core.risk.config.RiskProperties;
import com.finguard.core.risk.model.RiskRuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Component
public class LargeAmountRule implements RiskRule {

    private static final String REASON_SUMMARY =
            "Transaction amount reached configured large amount threshold";

    private final RiskProperties.LargeAmount properties;

    public LargeAmountRule(RiskProperties riskProperties) {
        this.properties = Objects.requireNonNull(
                riskProperties,
                "riskProperties must not be null"
        ).getLargeAmount();
    }

    @Override
    public RiskRuleCode ruleCode() {
        return RiskRuleCode.LARGE_AMOUNT;
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
        BigDecimal amount = context.currentTransaction().amount();
        BigDecimal threshold = properties.getThreshold();
        if (amount.compareTo(threshold) < 0) {
            return Optional.empty();
        }
        return Optional.of(RiskRuleResult.largeAmount(
                amount,
                threshold,
                REASON_SUMMARY
        ));
    }
}
