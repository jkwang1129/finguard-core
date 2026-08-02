package com.finguard.core.risk.rule;

import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.risk.config.RiskProperties;
import com.finguard.core.risk.model.RiskRuleCode;
import com.finguard.core.transaction.model.TransactionDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskRulesTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 2, 10, 0);
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    @Test
    void largeAmountShouldUseInclusiveBigDecimalThreshold() {
        LargeAmountRule rule = new LargeAmountRule(defaultProperties());

        assertThat(rule.evaluate(context(current(1, "9999.99"))))
                .isEmpty();
        assertThat(rule.evaluate(context(current(2, "10000.00"))))
                .get()
                .satisfies(result -> {
                    assertThat(result.ruleCode())
                            .isEqualTo(RiskRuleCode.LARGE_AMOUNT);
                    assertThat(result.observedAmount())
                            .isEqualByComparingTo("10000.00");
                    assertThat(result.thresholdAmount())
                            .isEqualByComparingTo("10000.00");
                });
        assertThat(rule.evaluate(context(current(3, "10000.01"))))
                .isPresent();
    }

    @Test
    void duplicateShouldIncludeFiveMinuteBoundaryButNotBeyondIt() {
        DuplicateTransactionRule rule =
                new DuplicateTransactionRule(defaultProperties());
        ReconciliationTransaction current = current(20, "88.00");
        ReconciliationTransaction boundary = transaction(
                10,
                1,
                TransactionDirection.EXPENSE,
                "88.00",
                BASE_TIME.minusMinutes(5)
        );

        assertThat(rule.evaluate(context(current, boundary)))
                .get()
                .satisfies(result -> {
                    assertThat(result.observedCount()).isEqualTo(1);
                    assertThat(result.thresholdCount()).isEqualTo(1);
                    assertThat(result.windowSeconds()).isEqualTo(300);
                });

        ReconciliationTransaction outside = transaction(
                11,
                1,
                TransactionDirection.EXPENSE,
                "88.00",
                BASE_TIME.minusMinutes(5).minusNanos(1_000_000)
        );
        assertThat(rule.evaluate(context(current, outside))).isEmpty();
    }

    @Test
    void duplicateShouldUseStableOrderAndBusinessFieldIsolation() {
        DuplicateTransactionRule rule =
                new DuplicateTransactionRule(defaultProperties());
        ReconciliationTransaction current = current(20, "88.00");

        assertThat(rule.evaluate(context(
                current,
                transaction(10, 1, TransactionDirection.EXPENSE,
                        "88.00", BASE_TIME),
                transaction(30, 1, TransactionDirection.EXPENSE,
                        "88.00", BASE_TIME),
                transaction(9, 1, TransactionDirection.INCOME,
                        "88.00", BASE_TIME.minusMinutes(1)),
                transaction(8, 1, TransactionDirection.EXPENSE,
                        "89.00", BASE_TIME.minusMinutes(1))
        ))).get().extracting(RiskRuleResult::observedCount)
                .isEqualTo(1);
    }

    @Test
    void frequentShouldHitFifthExpenseWithInclusiveWindow() {
        FrequentTransactionRule rule =
                new FrequentTransactionRule(defaultProperties());
        ReconciliationTransaction current = current(50, "10.00");
        List<ReconciliationTransaction> firstFour = List.of(
                transaction(10, 1, TransactionDirection.EXPENSE,
                        "1.00", BASE_TIME.minusMinutes(10)),
                transaction(20, 1, TransactionDirection.EXPENSE,
                        "2.00", BASE_TIME.minusMinutes(8)),
                transaction(30, 1, TransactionDirection.EXPENSE,
                        "3.00", BASE_TIME.minusMinutes(6)),
                transaction(40, 1, TransactionDirection.EXPENSE,
                        "4.00", BASE_TIME.minusMinutes(4))
        );

        assertThat(rule.evaluate(context(
                transaction(40, 1, TransactionDirection.EXPENSE,
                        "4.00", BASE_TIME.minusMinutes(4)),
                firstFour.toArray(ReconciliationTransaction[]::new)
        ))).isEmpty();

        List<ReconciliationTransaction> five =
                new java.util.ArrayList<>(firstFour);
        five.add(current);
        assertThat(rule.evaluate(context(
                current,
                five.toArray(ReconciliationTransaction[]::new)
        ))).get().satisfies(result -> {
            assertThat(result.observedCount()).isEqualTo(5);
            assertThat(result.thresholdCount()).isEqualTo(5);
            assertThat(result.windowSeconds()).isEqualTo(600);
        });
    }

    @Test
    void frequentShouldIgnoreIncomeOutsideWindowAndLaterSameTimeId() {
        FrequentTransactionRule rule =
                new FrequentTransactionRule(defaultProperties());
        ReconciliationTransaction current = current(50, "10.00");

        assertThat(rule.evaluate(context(
                current,
                transaction(1, 1, TransactionDirection.EXPENSE,
                        "1.00", BASE_TIME.minusMinutes(10).minusNanos(1_000_000)),
                transaction(2, 1, TransactionDirection.INCOME,
                        "1.00", BASE_TIME.minusMinutes(2)),
                transaction(3, 1, TransactionDirection.EXPENSE,
                        "1.00", BASE_TIME.minusMinutes(8)),
                transaction(4, 1, TransactionDirection.EXPENSE,
                        "1.00", BASE_TIME.minusMinutes(6)),
                transaction(5, 1, TransactionDirection.EXPENSE,
                        "1.00", BASE_TIME.minusMinutes(4)),
                transaction(60, 1, TransactionDirection.EXPENSE,
                        "1.00", BASE_TIME),
                current
        ))).isEmpty();
    }

    @Test
    void disabledRulesShouldNotEvaluate() {
        RiskProperties properties = defaultProperties();
        properties.getLargeAmount().setEnabled(false);
        properties.getPossibleDuplicate().setEnabled(false);
        properties.getFrequentTransaction().setEnabled(false);

        assertThat(new LargeAmountRule(properties).evaluate(
                context(current(1, "10000.00")))).isEmpty();
        assertThat(new DuplicateTransactionRule(properties).evaluate(
                context(current(2, "88.00"),
                        transaction(1, 1, TransactionDirection.EXPENSE,
                                "88.00", BASE_TIME.minusMinutes(1)))))
                .isEmpty();
        assertThat(new FrequentTransactionRule(properties).evaluate(
                context(current(5, "1.00"),
                        transaction(1, 1, TransactionDirection.EXPENSE,
                                "1.00", BASE_TIME.minusMinutes(4)),
                        transaction(2, 1, TransactionDirection.EXPENSE,
                                "1.00", BASE_TIME.minusMinutes(3)),
                        transaction(3, 1, TransactionDirection.EXPENSE,
                                "1.00", BASE_TIME.minusMinutes(2)),
                        transaction(4, 1, TransactionDirection.EXPENSE,
                                "1.00", BASE_TIME.minusMinutes(1)),
                        current(5, "1.00"))))
                .isEmpty();
    }

    private RiskEvaluationContext context(
            ReconciliationTransaction current,
            ReconciliationTransaction... candidates) {
        List<ReconciliationTransaction> history =
                candidates.length == 0 ? List.of(current) : List.of(candidates);
        return new RiskEvaluationContext(
                1L,
                current.id(),
                current,
                ReconciliationResultType.MATCHED,
                ReconciliationReasonCode.EXACT_MATCH,
                history,
                BUSINESS_ZONE
        );
    }

    private ReconciliationTransaction current(long id, String amount) {
        return transaction(
                id,
                1,
                TransactionDirection.EXPENSE,
                amount,
                BASE_TIME
        );
    }

    private ReconciliationTransaction transaction(
            long id,
            long accountId,
            TransactionDirection direction,
            String amount,
            LocalDateTime time) {
        return new ReconciliationTransaction(
                id,
                accountId,
                "RISK-" + id,
                direction,
                new BigDecimal(amount),
                time
        );
    }

    private RiskProperties defaultProperties() {
        return new RiskProperties();
    }
}
