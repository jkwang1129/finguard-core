package com.finguard.core.reconciliation;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.reconciliation.dto.ReconciliationResultQueryRequest;
import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.exception.InvalidReconciliationOperationException;
import com.finguard.core.reconciliation.mapper.ReconciliationResultMapper;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.support.ReconciliationTestFixture;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.reconciliation.vo.ReconciliationResultResponse;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionBusinessKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SuppressWarnings("unchecked")
class ReconciliationJobIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 7, 31, 9, 0);

    @Autowired
    private ReconciliationJobService reconciliationJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ReconciliationResultMapper reconciliationResultMapper;

    @MockitoSpyBean
    private TransactionMapper transactionMapper;

    private ReconciliationTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ReconciliationTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        reset(reconciliationResultMapper, transactionMapper);
        fixture.clean();
    }

    @Test
    void shouldPersistFourResultTypesAndReturnOriginalJob() {
        Scenario scenario = createFourTypeScenario();

        ReconciliationJobResponse created =
                reconciliationJobService.create(
                        scenario.importJobId(),
                        scenario.ownerId()
                );
        ReconciliationJobResponse duplicate =
                reconciliationJobService.create(
                        scenario.importJobId(),
                        scenario.ownerId()
                );

        assertThat(created.status())
                .isEqualTo(ReconciliationJobStatus.COMPLETED);
        assertThat(created.totalCount()).isEqualTo(5);
        assertThat(created.matchedCount()).isEqualTo(2);
        assertThat(created.unmatchedCount()).isEqualTo(1);
        assertThat(created.duplicateCount()).isEqualTo(1);
        assertThat(created.suspiciousCount()).isEqualTo(1);
        assertThat(created.duplicateRequest()).isFalse();
        assertThat(created.startedAt()).isNotNull();
        assertThat(created.finishedAt()).isNotNull();

        assertThat(duplicate.id()).isEqualTo(created.id());
        assertThat(duplicate.duplicateRequest()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reconciliation_jobs
                WHERE import_job_id = ?
                """,
                Integer.class,
                scenario.importJobId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reconciliation_results
                WHERE reconciliation_job_id = ?
                """,
                Integer.class,
                created.id()
        )).isEqualTo(5);

        PageResponse<ReconciliationResultResponse> page =
                reconciliationJobService.queryResults(
                        created.id(),
                        new ReconciliationResultQueryRequest(
                                1L,
                                10L,
                                null
                        )
                );
        assertThat(page.records())
                .extracting(ReconciliationResultResponse::resultType)
                .containsExactly(
                        ReconciliationResultType.MATCHED,
                        ReconciliationResultType.MATCHED,
                        ReconciliationResultType.UNMATCHED,
                        ReconciliationResultType.SUSPICIOUS,
                        ReconciliationResultType.DUPLICATE
                );
        assertThat(page.records())
                .extracting(
                        ReconciliationResultResponse::reasonCode
                )
                .containsExactly(
                        ReconciliationReasonCode.EXACT_MATCH,
                        ReconciliationReasonCode.TOLERANCE_MATCH,
                        ReconciliationReasonCode.NO_CANDIDATE,
                        ReconciliationReasonCode.AMOUNT_MISMATCH,
                        ReconciliationReasonCode.MULTIPLE_CANDIDATES
                );

        PageResponse<ReconciliationResultResponse> suspicious =
                reconciliationJobService.queryResults(
                        created.id(),
                        new ReconciliationResultQueryRequest(
                                1L,
                                20L,
                                ReconciliationResultType.SUSPICIOUS
                        )
                );
        assertThat(suspicious.total()).isEqualTo(1);
        assertThat(suspicious.records().get(0).manualTransactionId())
                .isNotNull();
    }

    @Test
    void shouldUseDatabaseConstraintForConcurrentDuplicateTriggers()
            throws Exception {
        Scenario scenario = createOneExactScenario();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReconciliationJobResponse> first =
                    executor.submit(() -> {
                        barrier.await();
                        return reconciliationJobService.create(
                                scenario.importJobId(),
                                scenario.ownerId()
                        );
                    });
            Future<ReconciliationJobResponse> second =
                    executor.submit(() -> {
                        barrier.await();
                        return reconciliationJobService.create(
                                scenario.importJobId(),
                                scenario.ownerId()
                        );
                    });

            List<ReconciliationJobResponse> responses =
                    List.of(first.get(), second.get());
            assertThat(responses)
                    .extracting(ReconciliationJobResponse::id)
                    .containsOnly(responses.get(0).id());
            assertThat(responses)
                    .extracting(
                            ReconciliationJobResponse::duplicateRequest
                    )
                    .containsExactlyInAnyOrder(false, true);
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM reconciliation_results
                    WHERE reconciliation_job_id = ?
                    """,
                    Integer.class,
                    responses.get(0).id()
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectImportJobThatIsNotReadyWithoutSideEffects() {
        Long ownerId = fixture.insertUser();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.FAILED,
                0
        );

        assertThatThrownBy(() -> reconciliationJobService.create(
                importJobId,
                ownerId
        )).isInstanceOf(
                InvalidReconciliationOperationException.class
        );
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reconciliation_jobs
                WHERE import_job_id = ?
                """,
                Integer.class,
                importJobId
        )).isZero();
    }

    @Test
    void shouldRollbackResultsAndRecoverFailedJob() {
        Scenario scenario = createOneExactScenario();
        doThrow(new DataAccessResourceFailureException(
                "injected result persistence failure"
        )).when(reconciliationResultMapper).insertBatch(
                org.mockito.ArgumentMatchers
                        .<List<ReconciliationResult>>any()
        );

        assertThatThrownBy(() -> reconciliationJobService.create(
                scenario.importJobId(),
                scenario.ownerId()
        )).isInstanceOf(DataAccessResourceFailureException.class);

        Long jobId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM reconciliation_jobs
                WHERE import_job_id = ?
                """,
                Long.class,
                scenario.importJobId()
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reconciliation_jobs WHERE id = ?",
                String.class,
                jobId
        )).isEqualTo(ReconciliationJobStatus.FAILED.name());
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT error_summary
                FROM reconciliation_jobs
                WHERE id = ?
                """,
                String.class,
                jobId
        )).isEqualTo("Reconciliation processing failed");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reconciliation_results
                WHERE reconciliation_job_id = ?
                """,
                Integer.class,
                jobId
        )).isZero();
    }

    @Test
    void shouldBatchMoreThanFiveHundredTransactionsWithoutNPlusOne() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        int rowCount = 501;
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                rowCount
        );
        for (int index = 0; index < rowCount; index++) {
            String externalNo = "BATCH-" + index;
            LocalDateTime time = BASE_TIME.plusSeconds(index);
            fixture.insertTransaction(
                    accountId,
                    importJobId,
                    externalNo,
                    TransactionDirection.INCOME,
                    "1.00",
                    time,
                    TransactionSource.CSV_IMPORT
            );
            fixture.insertTransaction(
                    accountId,
                    null,
                    externalNo,
                    TransactionDirection.INCOME,
                    "1.00",
                    time,
                    TransactionSource.MANUAL
            );
        }

        ReconciliationJobResponse response =
                reconciliationJobService.create(importJobId, ownerId);

        assertThat(response.totalCount()).isEqualTo(rowCount);
        assertThat(response.matchedCount()).isEqualTo(rowCount);
        verify(transactionMapper, times(1))
                .selectImportedByJobId(importJobId);
        verify(transactionMapper, times(2))
                .selectManualByBusinessKeys(
                        org.mockito.ArgumentMatchers
                                .<List<TransactionBusinessKey>>any()
                );
        verify(transactionMapper, times(1))
                .selectManualByAccountsAndTime(
                        anyList(),
                        org.mockito.ArgumentMatchers
                                .<LocalDateTime>any(),
                        org.mockito.ArgumentMatchers
                                .<LocalDateTime>any()
                );
        verify(reconciliationResultMapper, times(2))
                .insertBatch(
                        org.mockito.ArgumentMatchers
                                .<List<ReconciliationResult>>any()
                );
    }

    private Scenario createFourTypeScenario() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                5
        );

        fixture.insertTransaction(
                accountId,
                importJobId,
                "CSV-EXACT",
                TransactionDirection.INCOME,
                "100.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                importJobId,
                "CSV-WEAK",
                TransactionDirection.EXPENSE,
                "200.00",
                BASE_TIME.plusDays(1),
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                importJobId,
                "CSV-NONE",
                TransactionDirection.INCOME,
                "300.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                importJobId,
                "CSV-SUSPICIOUS",
                TransactionDirection.INCOME,
                "401.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                importJobId,
                "CSV-DUPLICATE",
                TransactionDirection.INCOME,
                "500.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );

        fixture.insertTransaction(
                accountId,
                null,
                "CSV-EXACT",
                TransactionDirection.INCOME,
                "100.0",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        fixture.insertTransaction(
                accountId,
                null,
                "MANUAL-WEAK",
                TransactionDirection.EXPENSE,
                "200.00",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        fixture.insertTransaction(
                accountId,
                null,
                "CSV-SUSPICIOUS",
                TransactionDirection.INCOME,
                "400.00",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        fixture.insertTransaction(
                accountId,
                null,
                "MANUAL-DUP-A",
                TransactionDirection.INCOME,
                "500.00",
                BASE_TIME.minusHours(1),
                TransactionSource.MANUAL
        );
        fixture.insertTransaction(
                accountId,
                null,
                "MANUAL-DUP-B",
                TransactionDirection.INCOME,
                "500.00",
                BASE_TIME.plusHours(1),
                TransactionSource.MANUAL
        );
        return new Scenario(ownerId, importJobId);
    }

    private Scenario createOneExactScenario() {
        Long ownerId = fixture.insertUser();
        Long accountId = fixture.insertAccount();
        Long importJobId = fixture.insertImportJob(
                ownerId,
                ImportJobStatus.SUCCESS,
                1
        );
        fixture.insertTransaction(
                accountId,
                importJobId,
                "ONE-EXACT",
                TransactionDirection.INCOME,
                "10.00",
                BASE_TIME,
                TransactionSource.CSV_IMPORT
        );
        fixture.insertTransaction(
                accountId,
                null,
                "ONE-EXACT",
                TransactionDirection.INCOME,
                "10.00",
                BASE_TIME,
                TransactionSource.MANUAL
        );
        return new Scenario(ownerId, importJobId);
    }

    private record Scenario(Long ownerId, Long importJobId) {
    }
}
