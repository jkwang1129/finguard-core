package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.importjob.mapper.ImportRowErrorMapper;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.service.impl.ImportJobTransactionService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.vo.ImportJobResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AsyncImportProcessorIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W4D4_PROC_";
    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private ImportJobTransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ImportRowErrorMapper importRowErrorMapper;

    private ImportJobTestFixture fixture;
    private Long ownerId;
    private String accountNo;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        accountNo = ACCOUNT_PREFIX + token();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_no, account_name, account_type,
                    currency, status, deleted
                )
                VALUES (?, 'Week 4 Day 4 processor',
                        'BANK', 'CNY', 'ACTIVE', 0)
                """,
                accountNo
        );
    }

    @AfterEach
    void tearDown() {
        reset(importRowErrorMapper);
        clean();
    }

    @Test
    void shouldRestorePersistedFileAndCompleteSuccessAndPartialResults() {
        ImportJobResponse success = accept(
                "success.csv",
                row(accountNo, "SUCCESS-1", "10.00")
        );
        transactionService.processPending(success.id());

        ImportJobResponse completed = importJobService.getById(success.id());
        assertThat(completed.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(completed.totalRows()).isEqualTo(1);
        assertThat(completed.successRows()).isEqualTo(1);
        assertThat(completed.failedRows()).isZero();
        assertThat(completed.startedAt()).isNotNull();
        assertThat(completed.finishedAt()).isNotNull();

        ImportJobResponse partial = accept(
                "partial.csv",
                row(accountNo, "PARTIAL-OK", "5.00"),
                row("MISSING_ACCOUNT", "PARTIAL-BAD", "5.00")
        );
        transactionService.processPending(partial.id());

        ImportJobResponse partialResult =
                importJobService.getById(partial.id());
        assertThat(partialResult.status())
                .isEqualTo(ImportJobStatus.PARTIAL_SUCCESS);
        assertThat(partialResult.totalRows()).isEqualTo(2);
        assertThat(partialResult.successRows()).isEqualTo(1);
        assertThat(partialResult.failedRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                partial.id()
        )).isEqualTo(1);
    }

    @Test
    void shouldCommitFileLevelFailureAsBusinessTerminalState() {
        ImportJobResponse accepted = importJobService.upload(
                "invalid-header.csv",
                "wrong,header\nvalue,value\n"
                        .getBytes(StandardCharsets.UTF_8),
                ownerId
        );

        transactionService.processPending(accepted.id());

        ImportJobResponse result = importJobService.getById(accepted.id());
        assertThat(result.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(result.fileErrorCode())
                .isEqualTo(ImportFileErrorCode.INVALID_HEADER);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    @Test
    void shouldRollbackToPendingWhenUnexpectedPersistenceFailureOccurs() {
        ImportJobResponse accepted = accept(
                "rollback.csv",
                row(accountNo, "ROLLBACK-OK", "10.00"),
                row("MISSING_ACCOUNT", "ROLLBACK-BAD", "5.00")
        );
        doThrow(new DataAccessResourceFailureException(
                "injected row error persistence failure"
        )).when(importRowErrorMapper).insertBatch(any());

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        ImportJobResponse rolledBack =
                importJobService.getById(accepted.id());
        assertThat(rolledBack.status()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(rolledBack.startedAt()).isNull();
        assertThat(rolledBack.finishedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_row_errors WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    @Test
    void shouldRejectMissingPersistedFileWithoutBusinessSideEffects() {
        ImportJobResponse accepted = accept(
                "missing-file.csv",
                row(accountNo, "MISSING-FILE", "10.00")
        );
        jdbcTemplate.update(
                "DELETE FROM import_job_files WHERE import_job_id = ?",
                accepted.id()
        );

        assertThatThrownBy(() ->
                transactionService.processPending(accepted.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Persisted import file is missing");

        ImportJobResponse unchanged =
                importJobService.getById(accepted.id());
        assertThat(unchanged.status()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(unchanged.startedAt()).isNull();
        assertThat(unchanged.finishedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                accepted.id()
        )).isZero();
    }

    private ImportJobResponse accept(String fileName, String... rows) {
        return importJobService.upload(
                fileName,
                (HEADER + "\n" + String.join("\n", rows) + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                ownerId
        );
    }

    private String row(
            String rowAccountNo,
            String externalNo,
            String amount) {
        return String.join(
                ",",
                rowAccountNo,
                externalNo + "-" + token(),
                "INCOME",
                amount,
                "2026-08-01 10:00:00",
                "async processor test"
        );
    }

    private String token() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
    }

    private void clean() {
        fixture.clean();
        jdbcTemplate.update(
                """
                DELETE t
                FROM transactions t
                INNER JOIN accounts a ON a.id = t.account_id
                WHERE a.account_no LIKE ?
                """,
                ACCOUNT_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM accounts WHERE account_no LIKE ?",
                ACCOUNT_PREFIX + "%"
        );
    }
}
