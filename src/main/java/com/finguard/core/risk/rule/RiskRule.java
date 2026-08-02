package com.finguard.core.risk.rule;

import com.finguard.core.risk.model.RiskRuleCode;
import org.springframework.core.Ordered;

import java.util.Optional;

public interface RiskRule extends Ordered {

    RiskRuleCode ruleCode();

    boolean enabled();

    Optional<RiskRuleResult> evaluate(RiskEvaluationContext context);

    @Override
    default int getOrder() {
        return ruleCode().getExecutionOrder();
    }
}
