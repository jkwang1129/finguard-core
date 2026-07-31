package com.finguard.core.importjob;

import com.finguard.core.importjob.mapper.ImportRowErrorMapper;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.validation.CsvImportRowValidator;
import com.finguard.core.importjob.validation.ImportFileValidationResult;
import com.finguard.core.importjob.validation.ValidatedImportRow;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SyncImportRecoveryIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W3D5_REC_";
    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private CsvImportRowValidator rowValidator;

    @MockitoSpyBean
    private ImportRowErrorMapper importRowErrorMapper;

    private ImportJobTestFixture fixture;
    private Long ownerId;
    private Long accountId;
    private String accountNo;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        accountNo = ACCOUNT_PREFIX
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_no,
                    account_name,
                    account_type,
                    currency,
                    status,
                    deleted
                )
                VALUES (?, ?, 'BANK', 'CNY', 'ACTIVE', 0)
                """,
                accountNo,
                "Week 3 Day 5 recovery"
        );
        accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE account_no = ?",
                Long.class,
                accountNo
        );
    }

    @AfterEach
    void tearDown() {
        reset(rowValidator, importRowErrorMapper);
        clean();
    }

    @Test
    void shouldDowngradeWriteTimeUniqueRaceToRowError() {
        ImportJobResponse original = importJobService.upload(
                "race-original.csv",
                csv("EXT-RACE"),
                ownerId
        );
        assertThat(original.status()).isEqualTo(ImportJobStatus.SUCCESS);

        doReturn(new ImportFileValidationResult(
                1,
                List.of(new ValidatedImportRow(
                        2,
                        accountId,
                        "EXT-RACE",
                        TransactionDirection.INCOME,
                        new BigDecimal("10.00"),
                        LocalDateTime.of(2026, 7, 31, 9, 0),
                        "race",
                        TransactionSource.CSV_IMPORT
                )),
                List.of()
        )).when(rowValidator).validate(any());

        ImportJobResponse raced = importJobService.upload(
                "race-second.csv",
                csv("EXT-DIFFERENT-BYTES"),
                ownerId
        );

        assertThat(raced.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(raced.totalRows()).isEqualTo(1);
        assertThat(raced.successRows()).isZero();
        assertThat(raced.failedRows()).isEqualTo(1);
        assertThat(raced.duplicateRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE account_id = ?
                  AND source = 'CSV_IMPORT'
                  AND external_transaction_no = 'EXT-RACE'
                """,
                Integer.class,
                accountId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT error_code
                FROM import_row_errors
                WHERE import_job_id = ?
                """,
                String.class,
                raced.id()
        )).isEqualTo(ImportRowErrorCode.DUPLICATE_TRANSACTION.name());
    }

    @Test
    void shouldRollbackBusinessWritesAndRecoverTaskInNewTransaction() {
        doThrow(new DataAccessResourceFailureException(
                "injected row error persistence failure"
        )).when(importRowErrorMapper).insertBatch(any());

        byte[] partial = (HEADER + "\n"
                + accountNo + ",EXT-ROLLBACK,INCOME,10.00,"
                + "2026-07-31 09:00:00,valid\n"
                + "MISSING_ACCOUNT,EXT-INVALID,EXPENSE,5.00,"
                + "2026-07-31 09:00:00,invalid\n")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importJobService.upload(
                "rollback.csv",
                partial,
                ownerId
        )).isInstanceOf(DataAccessResourceFailureException.class);

        Long importJobId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM import_jobs
                WHERE original_file_name = 'rollback.csv'
                  AND created_by = ?
                """,
                Long.class,
                ownerId
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM import_jobs WHERE id = ?",
                String.class,
                importJobId
        )).isEqualTo(ImportJobStatus.FAILED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT file_error_code FROM import_jobs WHERE id = ?",
                String.class,
                importJobId
        )).isEqualTo(ImportFileErrorCode.PROCESSING_FAILED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                importJobId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_row_errors
                WHERE import_job_id = ?
                """,
                Integer.class,
                importJobId
        )).isZero();
    }

    private byte[] csv(String externalTransactionNo) {
        return (HEADER + "\n"
                + accountNo + "," + externalTransactionNo
                + ",INCOME,10.00,2026-07-31 09:00:00,test\n")
                .getBytes(StandardCharsets.UTF_8);
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
