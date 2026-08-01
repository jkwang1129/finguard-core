package com.finguard.core.importjob;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.importjob.dto.ImportRowErrorQueryRequest;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.CsvImportFileParser;
import com.finguard.core.importjob.parser.PreparedImportFile;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.service.impl.ImportJobTransactionService;
import com.finguard.core.importjob.support.ImportJobTestFixture;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.importjob.vo.ImportRowErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SyncImportJobIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W3D5_";
    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private ImportJobTransactionService transactionService;

    @Autowired
    private CsvImportFileParser fileParser;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ImportJobTestFixture fixture;
    private Long ownerId;
    private String accountNo;

    @BeforeEach
    void setUp() {
        fixture = new ImportJobTestFixture(jdbcTemplate);
        clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        accountNo = ACCOUNT_PREFIX
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
        insertAccount(accountNo);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void shouldImportValidRowsAndReturnOriginalTaskForDuplicateFile() {
        String token = shortToken();
        byte[] bytes = csv(
                row(accountNo, token + "-1", "INCOME", "10.00"),
                row(accountNo, token + "-2", "EXPENSE", "20.50")
        );

        ImportJobResponse created = acceptAndProcess(
                "success.csv",
                bytes,
                ownerId
        );
        ImportJobResponse duplicate = importJobService.upload(
                "renamed.csv",
                bytes,
                ownerId
        );

        assertThat(created.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(created.totalRows()).isEqualTo(2);
        assertThat(created.successRows()).isEqualTo(2);
        assertThat(created.failedRows()).isZero();
        assertThat(created.duplicateRows()).isZero();
        assertThat(created.fileErrorCode()).isNull();
        assertThat(created.duplicateFile()).isFalse();
        assertThat(created.startedAt()).isNotNull();
        assertThat(created.finishedAt()).isNotNull();

        assertThat(duplicate.id()).isEqualTo(created.id());
        assertThat(duplicate.originalFileName()).isEqualTo("success.csv");
        assertThat(duplicate.duplicateFile()).isTrue();

        assertThat(transactionCount(token)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE import_job_id = ?
                  AND source = 'CSV_IMPORT'
                """,
                Integer.class,
                created.id()
        )).isEqualTo(2);
        assertThat(importJobService.getById(created.id()).duplicateFile())
                .isFalse();
        assertThat(importJobService.queryErrors(
                created.id(),
                new ImportRowErrorQueryRequest(null, null)
        ).records()).isEmpty();
    }

    @Test
    void shouldPersistPartialAndAllRejectedResultsWithStableErrors() {
        String token = shortToken();
        ImportJobResponse partial = acceptAndProcess(
                "partial.csv",
                csv(
                        row(accountNo, token + "-OK", "INCOME", "10.00"),
                        row("MISSING_ACCOUNT", token + "-BAD",
                                "EXPENSE", "5.00"),
                        row(accountNo, token + "-OK", "INCOME", "10.00")
                ),
                ownerId
        );

        assertThat(partial.status())
                .isEqualTo(ImportJobStatus.PARTIAL_SUCCESS);
        assertThat(partial.totalRows()).isEqualTo(3);
        assertThat(partial.successRows()).isEqualTo(1);
        assertThat(partial.failedRows()).isEqualTo(2);
        assertThat(partial.duplicateRows()).isEqualTo(1);
        assertThat(transactionCount(token)).isEqualTo(1);

        PageResponse<ImportRowErrorResponse> errors =
                importJobService.queryErrors(
                        partial.id(),
                        new ImportRowErrorQueryRequest(1L, 1L)
                );
        assertThat(errors.total()).isEqualTo(2);
        assertThat(errors.pages()).isEqualTo(2);
        assertThat(errors.records()).hasSize(1);
        assertThat(errors.records().get(0).rowNumber()).isEqualTo(3);
        assertThat(errors.records().get(0).errorCode())
                .isEqualTo(ImportRowErrorCode.ACCOUNT_NOT_FOUND);

        ImportJobResponse failed = acceptAndProcess(
                "all-rejected.csv",
                csv(row(
                        "MISSING_ACCOUNT",
                        token + "-ALL-BAD",
                        "EXPENSE",
                        "5.00"
                )),
                ownerId
        );
        assertThat(failed.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(failed.totalRows()).isEqualTo(1);
        assertThat(failed.successRows()).isZero();
        assertThat(failed.failedRows()).isEqualTo(1);
        assertThat(failed.fileErrorCode()).isNull();
    }

    @Test
    void shouldCreateFailedTaskForFileLevelErrorWithoutTransactions() {
        String invalid = "wrong,header\nvalue,value\n";

        ImportJobResponse response = acceptAndProcess(
                "invalid-header.csv",
                invalid.getBytes(StandardCharsets.UTF_8),
                ownerId
        );

        assertThat(response.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(response.fileErrorCode())
                .isEqualTo(ImportFileErrorCode.INVALID_HEADER);
        assertThat(response.totalRows()).isZero();
        assertThat(response.successRows()).isZero();
        assertThat(response.failedRows()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE import_job_id = ?",
                Integer.class,
                response.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_row_errors
                WHERE import_job_id = ?
                """,
                Integer.class,
                response.id()
        )).isZero();
    }

    @Test
    void shouldPersistEveryFileLevelFailureCode() {
        List<FileFailureCase> cases = List.of(
                new FileFailureCase(
                        "invalid-utf8.csv",
                        new byte[]{(byte) 0xC3, (byte) 0x28},
                        ImportFileErrorCode.INVALID_UTF8
                ),
                new FileFailureCase(
                        "invalid-header-all.csv",
                        "wrong,header\nvalue,value\n"
                                .getBytes(StandardCharsets.UTF_8),
                        ImportFileErrorCode.INVALID_HEADER
                ),
                new FileFailureCase(
                        "malformed.csv",
                        (HEADER + "\n"
                                + accountNo + ",EXT-MALFORMED,INCOME,"
                                + "10.00,2026-07-31 09:00:00,\"open")
                                .getBytes(StandardCharsets.UTF_8),
                        ImportFileErrorCode.MALFORMED_CSV
                ),
                new FileFailureCase(
                        "no-data.csv",
                        (HEADER + "\n").getBytes(StandardCharsets.UTF_8),
                        ImportFileErrorCode.NO_DATA_ROWS
                ),
                new FileFailureCase(
                        "too-many.csv",
                        csvWithRows(10_001),
                        ImportFileErrorCode.TOO_MANY_ROWS
                )
        );

        for (FileFailureCase failureCase : cases) {
            ImportJobResponse response = acceptAndProcess(
                    failureCase.fileName(),
                    failureCase.bytes(),
                    ownerId
            );
            assertThat(response.status())
                    .isEqualTo(ImportJobStatus.FAILED);
            assertThat(response.fileErrorCode())
                    .isEqualTo(failureCase.expectedCode());
            assertThat(response.totalRows()).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM transactions
                    WHERE import_job_id = ?
                    """,
                    Integer.class,
                    response.id()
            )).isZero();
        }
    }

    @Test
    void shouldBatchMoreThanFiveHundredRows() {
        String token = shortToken();
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            rows.add(row(
                    accountNo,
                    token + "-" + index,
                    "INCOME",
                    "1.00"
            ));
        }

        ImportJobResponse response = acceptAndProcess(
                "batch.csv",
                csv(rows.toArray(String[]::new)),
                ownerId
        );

        assertThat(response.status()).isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(response.totalRows()).isEqualTo(501);
        assertThat(response.successRows()).isEqualTo(501);
        assertThat(transactionCount(token)).isEqualTo(501);
    }

    @Test
    void manualKeyShouldCoexistAndDeletedCsvShouldRemainDuplicate() {
        String transactionNo = shortToken() + "-SAME";
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE account_no = ?",
                Long.class,
                accountNo
        );
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    account_id,
                    external_transaction_no,
                    direction,
                    amount,
                    transaction_time,
                    description,
                    source,
                    deleted
                )
                VALUES (?, ?, 'INCOME', 10.00,
                        '2026-07-31 09:00:00',
                        'manual side', 'MANUAL', 0)
                """,
                accountId,
                transactionNo
        );

        ImportJobResponse firstCsv = acceptAndProcess(
                "same-key.csv",
                csv(row(
                        accountNo,
                        transactionNo,
                        "INCOME",
                        "10.00"
                )),
                ownerId
        );
        assertThat(firstCsv.status())
                .isEqualTo(ImportJobStatus.SUCCESS);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE account_id = ?
                  AND external_transaction_no = ?
                """,
                Integer.class,
                accountId,
                transactionNo
        )).isEqualTo(2);

        jdbcTemplate.update(
                """
                UPDATE transactions
                SET deleted = 1
                WHERE import_job_id = ?
                  AND external_transaction_no = ?
                """,
                firstCsv.id(),
                transactionNo
        );
        byte[] changedBytes = (HEADER + "\n"
                + accountNo + "," + transactionNo
                + ",INCOME,10.00,2026-07-31 09:00:00,"
                + "changed bytes\n").getBytes(StandardCharsets.UTF_8);
        ImportJobResponse secondCsv = acceptAndProcess(
                "same-key-changed.csv",
                changedBytes,
                ownerId
        );

        assertThat(secondCsv.status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(secondCsv.successRows()).isZero();
        assertThat(secondCsv.failedRows()).isEqualTo(1);
        assertThat(secondCsv.duplicateRows()).isEqualTo(1);
    }

    @Test
    void shouldUseHashConstraintForConcurrentDuplicateUploads()
            throws Exception {
        String token = shortToken();
        byte[] bytes = csv(
                row(accountNo, token + "-CONCURRENT",
                        "INCOME", "8.00")
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ImportJobResponse> first = executor.submit(() -> {
                barrier.await();
                return acceptAndProcess(
                        "concurrent-a.csv",
                        bytes,
                        ownerId
                );
            });
            Future<ImportJobResponse> second = executor.submit(() -> {
                barrier.await();
                return acceptAndProcess(
                        "concurrent-b.csv",
                        bytes,
                        ownerId
                );
            });

            List<ImportJobResponse> responses =
                    List.of(first.get(), second.get());
            assertThat(responses)
                    .extracting(ImportJobResponse::id)
                    .containsOnly(responses.get(0).id());
            assertThat(responses)
                    .extracting(ImportJobResponse::duplicateFile)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(transactionCount(token)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM import_jobs WHERE file_hash = ?",
                    Integer.class,
                    responses.get(0).fileHash()
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertAccount(String value) {
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
                value,
                "Week 3 Day 5 " + value
        );
    }

    private ImportJobResponse acceptAndProcess(
            String fileName,
            byte[] bytes,
            Long createdBy) {
        ImportJobResponse accepted = importJobService.upload(
                fileName,
                bytes,
                createdBy
        );
        if (accepted.duplicateFile()) {
            return accepted;
        }
        PreparedImportFile prepared = fileParser.prepare(fileName, bytes);
        transactionService.markProcessing(accepted.id());
        try {
            transactionService.processAndComplete(
                    accepted.id(),
                    prepared
            );
        } catch (RuntimeException exception) {
            transactionService.markProcessingFailed(accepted.id());
            throw exception;
        }
        return importJobService.getById(accepted.id());
    }

    private byte[] csv(String... rows) {
        return (HEADER + "\n" + String.join("\n", rows) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String row(
            String rowAccountNo,
            String externalTransactionNo,
            String direction,
            String amount) {
        return String.join(
                ",",
                rowAccountNo,
                externalTransactionNo,
                direction,
                amount,
                "2026-07-31 09:00:00",
                "sync import test"
        );
    }

    private String shortToken() {
        return "D5-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private byte[] csvWithRows(int rowCount) {
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (int index = 0; index < rowCount; index++) {
            content.append(accountNo)
                    .append(",TOO-MANY-")
                    .append(index)
                    .append(",INCOME,1.00,")
                    .append("2026-07-31 09:00:00,test\n");
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int transactionCount(String token) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions
                WHERE external_transaction_no LIKE ?
                """,
                Integer.class,
                token + "%"
        );
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

    private record FileFailureCase(
            String fileName,
            byte[] bytes,
            ImportFileErrorCode expectedCode) {
    }
}
