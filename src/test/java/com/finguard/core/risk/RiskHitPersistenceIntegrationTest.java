package com.finguard.core.risk;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.risk.entity.RiskHit;
import com.finguard.core.risk.mapper.RiskHitMapper;
import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RiskHitPersistenceIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 2, 9, 0);

    @Autowired
    private RiskHitMapper riskHitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void shouldBatchPersistAndReloadAllLegalShapesInRuleOrder() {
        Long resultId = insertMatchedResult();

        int inserted = riskHitMapper.insertBatch(List.of(
                largeAmountHit(resultId),
                possibleDuplicateHit(resultId),
                frequentTransactionHit(resultId)
        ));
        List<RiskHit> hits =
                riskHitMapper.selectByReconciliationResultIds(
                        List.of(resultId)
                );

        assertThat(inserted).isEqualTo(3);
        assertThat(hits).hasSize(3);
        assertThat(hits)
                .extracting(RiskHit::getRuleCode)
                .containsExactly(
                        RiskRuleCode.LARGE_AMOUNT,
                        RiskRuleCode.POSSIBLE_DUPLICATE,
                        RiskRuleCode.FREQUENT_TRANSACTION
                );
        assertThat(hits)
                .extracting(RiskHit::getReasonCode)
                .containsExactly(
                        RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD,
                        RiskReasonCode.SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME,
                        RiskReasonCode.EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD
                );
        assertThat(hits.get(0).getObservedAmount())
                .isEqualByComparingTo("12000.00");
        assertThat(hits.get(0).getThresholdAmount())
                .isEqualByComparingTo("10000.00");
        assertThat(hits.get(1).getObservedCount()).isEqualTo(2);
        assertThat(hits.get(1).getThresholdCount()).isEqualTo(1);
        assertThat(hits.get(1).getWindowSeconds()).isEqualTo(300);
        assertThat(hits.get(2).getObservedCount()).isEqualTo(5);
        assertThat(hits.get(2).getThresholdCount()).isEqualTo(5);
        assertThat(hits.get(2).getWindowSeconds()).isEqualTo(600);
        assertThat(hits).allSatisfy(hit -> {
            assertThat(hit.getId()).isPositive();
            assertThat(hit.getReconciliationResultId())
                    .isEqualTo(resultId);
            assertThat(hit.getReasonSummary()).isNotBlank();
            assertThat(hit.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void shouldKeepDifferentRulesButRejectDuplicateResultRule() {
        Long resultId = insertMatchedResult();
        assertThat(riskHitMapper.insertBatch(List.of(
                largeAmountHit(resultId),
                possibleDuplicateHit(resultId)
        ))).isEqualTo(2);

        assertThatThrownBy(() -> riskHitMapper.insertBatch(
                List.of(largeAmountHit(resultId))
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM risk_hits
                WHERE reconciliation_result_id = ?
                """,
                Integer.class,
                resultId
        )).isEqualTo(2);
    }

    @Test
    void shouldRejectUnknownResultAndRestrictDeletingEvidenceSource() {
        Long resultId = insertMatchedResult();

        assertThatThrownBy(() -> riskHitMapper.insertBatch(
                List.of(largeAmountHit(Long.MAX_VALUE))
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(riskHitMapper.insertBatch(
                List.of(largeAmountHit(resultId))
        )).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM reconciliation_results WHERE id = ?",
                resultId
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_hits WHERE reconciliation_result_id = ?",
                Integer.class,
                resultId
        )).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidRuleReasonValueAndSummaryShapes() {
        Long resultId = insertMatchedResult();

        assertInvalidRawHit(
                resultId,
                "LARGE_AMOUNT",
                "SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME",
                "12000.00",
                "10000.00",
                null,
                null,
                null,
                "mismatched reason"
        );
        assertInvalidRawHit(
                resultId,
                "LARGE_AMOUNT",
                "AMOUNT_AT_OR_ABOVE_THRESHOLD",
                "9999.99",
                "10000.00",
                null,
                null,
                null,
                "below threshold"
        );
        assertInvalidRawHit(
                resultId,
                "POSSIBLE_DUPLICATE",
                "SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME",
                null,
                null,
                2,
                2,
                300,
                "wrong fixed threshold"
        );
        assertInvalidRawHit(
                resultId,
                "FREQUENT_TRANSACTION",
                "EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD",
                "1.00",
                null,
                5,
                5,
                600,
                "mixed amount and count"
        );
        assertInvalidRawHit(
                resultId,
                "FREQUENT_TRANSACTION",
                "EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD",
                null,
                null,
                0,
                5,
                600,
                "non-positive value"
        );
        assertInvalidRawHit(
                resultId,
                "UNKNOWN_RULE",
                "EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD",
                null,
                null,
                5,
                5,
                600,
                "unknown rule"
        );
        assertInvalidRawHit(
                resultId,
                "LARGE_AMOUNT",
                "AMOUNT_AT_OR_ABOVE_THRESHOLD",
                "12000.00",
                "10000.00",
                null,
                null,
                null,
                "   "
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_hits WHERE reconciliation_result_id = ?",
                Integer.class,
                resultId
        )).isZero();
    }

    private void assertInvalidRawHit(
            Long resultId,
            String ruleCode,
            String reasonCode,
            String observedAmount,
            String thresholdAmount,
            Integer observedCount,
            Integer thresholdCount,
            Integer windowSeconds,
            String reasonSummary) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO risk_hits (
                    reconciliation_result_id,
                    rule_code,
                    reason_code,
                    observed_amount,
                    threshold_amount,
                    observed_count,
                    threshold_count,
                    window_seconds,
                    reason_summary
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                resultId,
                ruleCode,
                reasonCode,
                observedAmount == null
                        ? null : new BigDecimal(observedAmount),
                thresholdAmount == null
                        ? null : new BigDecimal(thresholdAmount),
                observedCount,
                thresholdCount,
                windowSeconds,
                reasonSummary
        )).isInstanceOf(DataAccessException.class);
    }

    private Long insertMatchedResult() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        Long csvTransactionId = fixture.insertTransaction(
                accountId,
                importJobId,
                "RISK-CSV-" + System.nanoTime(),
                TransactionDirection.EXPENSE,
                "12000.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        Long manualTransactionId = fixture.insertTransaction(
                accountId,
                null,
                "RISK-MANUAL-" + System.nanoTime(),
                TransactionDirection.EXPENSE,
                "12000.00",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_jobs (
                    import_job_id,
                    status,
                    total_count,
                    matched_count,
                    unmatched_count,
                    duplicate_count,
                    suspicious_count,
                    created_by,
                    started_at,
                    finished_at
                )
                VALUES (?, 'COMPLETED', 1, 1, 0, 0, 0, ?, ?, ?)
                """,
                importJobId,
                ownerId,
                BASE_TIME,
                BASE_TIME.plusSeconds(1)
        );
        Long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM reconciliation_jobs WHERE import_job_id = ?",
                Long.class,
                importJobId
        );
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_results (
                    reconciliation_job_id,
                    csv_transaction_id,
                    manual_transaction_id,
                    result_type,
                    match_method,
                    reason_code
                )
                VALUES (?, ?, ?, 'MATCHED', 'EXACT', 'EXACT_MATCH')
                """,
                jobId,
                csvTransactionId,
                manualTransactionId
        );
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM reconciliation_results
                WHERE reconciliation_job_id = ?
                  AND csv_transaction_id = ?
                """,
                Long.class,
                jobId,
                csvTransactionId
        );
    }

    private RiskHit largeAmountHit(Long resultId) {
        RiskHit hit = baseHit(
                resultId,
                RiskRuleCode.LARGE_AMOUNT,
                RiskReasonCode.AMOUNT_AT_OR_ABOVE_THRESHOLD,
                "Amount is at or above configured threshold"
        );
        hit.setObservedAmount(new BigDecimal("12000.00"));
        hit.setThresholdAmount(new BigDecimal("10000.00"));
        return hit;
    }

    private RiskHit possibleDuplicateHit(Long resultId) {
        RiskHit hit = baseHit(
                resultId,
                RiskRuleCode.POSSIBLE_DUPLICATE,
                RiskReasonCode.SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME,
                "Similar earlier transaction exists in configured window"
        );
        hit.setObservedCount(2);
        hit.setThresholdCount(1);
        hit.setWindowSeconds(300);
        return hit;
    }

    private RiskHit frequentTransactionHit(Long resultId) {
        RiskHit hit = baseHit(
                resultId,
                RiskRuleCode.FREQUENT_TRANSACTION,
                RiskReasonCode.EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD,
                "Expense count is at or above configured threshold"
        );
        hit.setObservedCount(5);
        hit.setThresholdCount(5);
        hit.setWindowSeconds(600);
        return hit;
    }

    private RiskHit baseHit(
            Long resultId,
            RiskRuleCode ruleCode,
            RiskReasonCode reasonCode,
            String reasonSummary) {
        RiskHit hit = new RiskHit();
        hit.setReconciliationResultId(resultId);
        hit.setRuleCode(ruleCode);
        hit.setReasonCode(reasonCode);
        hit.setReasonSummary(reasonSummary);
        return hit;
    }
}
