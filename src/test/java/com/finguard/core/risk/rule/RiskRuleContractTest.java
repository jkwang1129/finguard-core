package com.finguard.core.risk.rule;

import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;
import com.finguard.core.transaction.model.TransactionDirection;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskRuleContractTest {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    @Test
    void contextShouldValidateFactsAndDefensivelyCopyCandidates() {
        ReconciliationTransaction current = transaction(1L, 10L);
        List<ReconciliationTransaction> mutableCandidates =
                new ArrayList<>(List.of(transaction(2L, 10L)));

        RiskEvaluationContext context = new RiskEvaluationContext(
                100L,
                200L,
                current,
                ReconciliationResultType.MATCHED,
                ReconciliationReasonCode.EXACT_MATCH,
                mutableCandidates,
                BUSINESS_ZONE
        );
        mutableCandidates.add(transaction(3L, 10L));

        assertThat(context.historicalTransactions()).hasSize(1);
        assertThatThrownBy(() -> context.historicalTransactions().add(
                transaction(4L, 10L)
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new RiskEvaluationContext(
                100L,
                200L,
                current,
                ReconciliationResultType.MATCHED,
                ReconciliationReasonCode.EXACT_MATCH,
                List.of(transaction(5L, 11L)),
                BUSINESS_ZONE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current account");
        assertThatThrownBy(() -> new RiskEvaluationContext(
                0L,
                200L,
                current,
                ReconciliationResultType.MATCHED,
                ReconciliationReasonCode.EXACT_MATCH,
                List.of(),
                BUSINESS_ZONE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reconciliationJobId");
    }

    @Test
    void factoriesShouldCreateThreeValidImmutableResultShapes() {
        RiskRuleResult large = RiskRuleResult.largeAmount(
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                "  Amount threshold reached  "
        );
        RiskRuleResult duplicate = RiskRuleResult.possibleDuplicate(
                2,
                300,
                "Possible duplicate found"
        );
        RiskRuleResult frequent = RiskRuleResult.frequentTransaction(
                5,
                5,
                600,
                "Frequent expense threshold reached"
        );

        assertThat(large.ruleCode()).isEqualTo(RiskRuleCode.LARGE_AMOUNT);
        assertThat(large.reasonCode()).isEqualTo(
                RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD
        );
        assertThat(large.reasonSummary())
                .isEqualTo("Amount threshold reached");
        assertThat(duplicate.thresholdCount()).isEqualTo(1);
        assertThat(duplicate.windowSeconds()).isEqualTo(300);
        assertThat(frequent.observedCount()).isEqualTo(5);
        assertThat(frequent.thresholdCount()).isEqualTo(5);
        assertThat(frequent.windowSeconds()).isEqualTo(600);
    }

    @Test
    void resultShouldRejectMismatchedOrInvalidSnapshots() {
        assertThatThrownBy(() -> new RiskRuleResult(
                RiskRuleCode.LARGE_AMOUNT,
                RiskReasonCode.SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME,
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                null,
                null,
                null,
                "Mismatched reason"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belong");
        assertThatThrownBy(() -> RiskRuleResult.largeAmount(
                new BigDecimal("10000.001"),
                new BigDecimal("10000.00"),
                "Invalid scale"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> RiskRuleResult.largeAmount(
                new BigDecimal("9999.99"),
                new BigDecimal("10000.00"),
                "Below threshold"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reach");
        assertThatThrownBy(() -> RiskRuleResult.frequentTransaction(
                4,
                5,
                600,
                "Below threshold"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reach");
        assertThatThrownBy(() -> RiskRuleResult.possibleDuplicate(
                1,
                0,
                "Invalid window"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowSeconds");
        assertThatThrownBy(() -> RiskRuleResult.frequentTransaction(
                5,
                5,
                600,
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> RiskRuleResult.frequentTransaction(
                5,
                5,
                600,
                "x".repeat(256)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    void rulesShouldExposeEnabledOptionalResultAndStableSpringOrder() {
        RiskEvaluationContext context = context();
        RiskRuleResult result = RiskRuleResult.largeAmount(
                new BigDecimal("12000.00"),
                new BigDecimal("10000.00"),
                "Amount threshold reached"
        );
        List<RiskRule> rules = new ArrayList<>(List.of(
                new StubRule(
                        RiskRuleCode.FREQUENT_TRANSACTION,
                        true,
                        Optional.empty()
                ),
                new StubRule(
                        RiskRuleCode.LARGE_AMOUNT,
                        true,
                        Optional.of(result)
                ),
                new StubRule(
                        RiskRuleCode.POSSIBLE_DUPLICATE,
                        false,
                        Optional.empty()
                )
        ));

        AnnotationAwareOrderComparator.sort(rules);

        assertThat(rules)
                .extracting(RiskRule::ruleCode)
                .containsExactly(
                        RiskRuleCode.LARGE_AMOUNT,
                        RiskRuleCode.POSSIBLE_DUPLICATE,
                        RiskRuleCode.FREQUENT_TRANSACTION
                );
        assertThat(rules.get(0).enabled()).isTrue();
        assertThat(rules.get(0).evaluate(context)).contains(result);
        assertThat(rules.get(1).enabled()).isFalse();
        assertThat(rules.get(1).evaluate(context)).isEmpty();
    }

    @Test
    void reasonCodesShouldHaveOneStableOwningRule() {
        assertThat(RiskReasonCode.values()).allSatisfy(reasonCode ->
                assertThat(reasonCode.belongsTo(reasonCode.getRuleCode()))
                        .isTrue()
        );
        assertThat(
                RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD.belongsTo(
                        RiskRuleCode.FREQUENT_TRANSACTION
                )
        ).isFalse();
    }

    private RiskEvaluationContext context() {
        return new RiskEvaluationContext(
                100L,
                200L,
                transaction(1L, 10L),
                ReconciliationResultType.MATCHED,
                ReconciliationReasonCode.EXACT_MATCH,
                List.of(),
                BUSINESS_ZONE
        );
    }

    private ReconciliationTransaction transaction(Long id, Long accountId) {
        return new ReconciliationTransaction(
                id,
                accountId,
                "TX-" + id,
                TransactionDirection.EXPENSE,
                new BigDecimal("12000.00"),
                LocalDateTime.of(2026, 8, 2, 9, 0)
        );
    }

    private record StubRule(
            RiskRuleCode ruleCode,
            boolean enabled,
            Optional<RiskRuleResult> result
    ) implements RiskRule {

        @Override
        public Optional<RiskRuleResult> evaluate(
                RiskEvaluationContext context) {
            return result;
        }
    }
}
