package com.finguard.core.reconciliation.service;

import com.finguard.core.reconciliation.model.ReconciliationDecision;
import com.finguard.core.reconciliation.model.ReconciliationMatchMethod;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.transaction.model.TransactionDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationMatcherTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 7, 31, 9, 0);

    private final ReconciliationMatcher matcher =
            new ReconciliationMatcher();

    @Test
    void shouldClassifyExactAndInclusiveToleranceMatches() {
        List<ReconciliationDecision> decisions = matcher.match(
                List.of(
                        tx(10, "EXACT", "10.00", BASE_TIME),
                        tx(11, "EARLY", "20.00", BASE_TIME),
                        tx(12, "LATE", "30.00", BASE_TIME)
                ),
                List.of(
                        tx(20, "EXACT", "10.0", BASE_TIME),
                        tx(21, "EARLY", "20.00",
                                BASE_TIME.minusDays(3)),
                        tx(22, "LATE", "30.00",
                                BASE_TIME.plusDays(3))
                )
        );

        assertThat(decisions)
                .extracting(
                        ReconciliationDecision::resultType,
                        ReconciliationDecision::matchMethod,
                        ReconciliationDecision::reasonCode
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ReconciliationResultType.MATCHED,
                                ReconciliationMatchMethod.EXACT,
                                ReconciliationReasonCode.EXACT_MATCH
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                ReconciliationResultType.MATCHED,
                                ReconciliationMatchMethod.TOLERANCE,
                                ReconciliationReasonCode.TOLERANCE_MATCH
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                ReconciliationResultType.MATCHED,
                                ReconciliationMatchMethod.TOLERANCE,
                                ReconciliationReasonCode.TOLERANCE_MATCH
                        )
                );
    }

    @Test
    void shouldUseStableSuspiciousReasonPriority() {
        List<ReconciliationDecision> decisions = matcher.match(
                List.of(
                        tx(10, "DIR", TransactionDirection.INCOME,
                                "10.00", BASE_TIME),
                        tx(11, "AMOUNT", "20.00", BASE_TIME),
                        tx(12, "TIME", "30.00", BASE_TIME)
                ),
                List.of(
                        tx(20, "DIR", TransactionDirection.EXPENSE,
                                "99.00", BASE_TIME.plusDays(9)),
                        tx(21, "AMOUNT", "21.00",
                                BASE_TIME.plusDays(9)),
                        tx(22, "TIME", "30.00",
                                BASE_TIME.plusDays(4))
                )
        );

        assertThat(decisions)
                .extracting(ReconciliationDecision::reasonCode)
                .containsExactly(
                        ReconciliationReasonCode.DIRECTION_MISMATCH,
                        ReconciliationReasonCode.AMOUNT_MISMATCH,
                        ReconciliationReasonCode.TIME_OUT_OF_RANGE
                );
        assertThat(decisions)
                .extracting(ReconciliationDecision::resultType)
                .containsOnly(ReconciliationResultType.SUSPICIOUS);
    }

    @Test
    void shouldWeakMatchOnlyOneCandidateAndKeepCaseSensitiveKeys() {
        ReconciliationDecision decision = matcher.match(
                List.of(tx(10, "lower", "10.00", BASE_TIME)),
                List.of(tx(20, "LOWER", "10.0",
                        BASE_TIME.plusHours(1)))
        ).get(0);

        assertThat(decision.resultType())
                .isEqualTo(ReconciliationResultType.MATCHED);
        assertThat(decision.matchMethod())
                .isEqualTo(ReconciliationMatchMethod.TOLERANCE);
        assertThat(decision.manualTransactionId()).isEqualTo(20L);
    }

    @Test
    void shouldClassifyNoCandidateAndMultipleCandidates() {
        List<ReconciliationDecision> decisions = matcher.match(
                List.of(
                        tx(10, "NONE", "10.00", BASE_TIME),
                        tx(11, "MANY", "20.00", BASE_TIME)
                ),
                List.of(
                        tx(20, "OTHER-A", "20.0", BASE_TIME),
                        tx(21, "OTHER-B", "20.00",
                                BASE_TIME.plusHours(1))
                )
        );

        assertThat(decisions)
                .extracting(
                        ReconciliationDecision::resultType,
                        ReconciliationDecision::reasonCode
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ReconciliationResultType.UNMATCHED,
                                ReconciliationReasonCode.NO_CANDIDATE
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                ReconciliationResultType.DUPLICATE,
                                ReconciliationReasonCode
                                        .MULTIPLE_CANDIDATES
                        )
                );
    }

    @Test
    void strongCandidateShouldReserveManualBeforeWeakPass() {
        List<ReconciliationDecision> decisions = matcher.match(
                List.of(
                        tx(10, "WEAK-FIRST", "10.00", BASE_TIME),
                        tx(11, "STRONG", "10.00", BASE_TIME)
                ),
                List.of(tx(20, "STRONG", "10.00", BASE_TIME))
        );

        assertThat(decisions.get(0).resultType())
                .isEqualTo(ReconciliationResultType.DUPLICATE);
        assertThat(decisions.get(0).reasonCode())
                .isEqualTo(
                        ReconciliationReasonCode.MANUAL_ALREADY_MATCHED
                );
        assertThat(decisions.get(1).resultType())
                .isEqualTo(ReconciliationResultType.MATCHED);
    }

    @Test
    void priorWeakMatchShouldMakeLaterCompetitionDuplicate() {
        List<ReconciliationDecision> decisions = matcher.match(
                List.of(
                        tx(10, "CSV-A", "10.00", BASE_TIME),
                        tx(11, "CSV-B", "10.00", BASE_TIME)
                ),
                List.of(tx(20, "MANUAL", "10.00", BASE_TIME))
        );

        assertThat(decisions)
                .extracting(ReconciliationDecision::resultType)
                .containsExactly(
                        ReconciliationResultType.MATCHED,
                        ReconciliationResultType.DUPLICATE
                );
        assertThat(decisions.get(1).reasonCode())
                .isEqualTo(
                        ReconciliationReasonCode.MANUAL_ALREADY_MATCHED
                );
    }

    @Test
    void shouldSortResultsByCsvIdRegardlessOfInputOrder() {
        List<ReconciliationDecision> decisions = matcher.match(
                List.of(
                        tx(12, "C", "30.00", BASE_TIME),
                        tx(10, "A", "10.00", BASE_TIME),
                        tx(11, "B", "20.00", BASE_TIME)
                ),
                List.of()
        );

        assertThat(decisions)
                .extracting(ReconciliationDecision::csvTransactionId)
                .containsExactly(10L, 11L, 12L);
    }

    private ReconciliationTransaction tx(
            long id,
            String externalNo,
            String amount,
            LocalDateTime time) {
        return tx(
                id,
                externalNo,
                TransactionDirection.INCOME,
                amount,
                time
        );
    }

    private ReconciliationTransaction tx(
            long id,
            String externalNo,
            TransactionDirection direction,
            String amount,
            LocalDateTime time) {
        return new ReconciliationTransaction(
                id,
                1L,
                externalNo,
                direction,
                new BigDecimal(amount),
                time
        );
    }
}
