package com.finguard.core.importjob.validation;

import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.ParsedCsvRow;
import com.finguard.core.importjob.parser.ParsedImportFile;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CsvImportRowValidationIntegrationTest {

    private static final String ACCOUNT_PREFIX = "W3D4_";

    @Autowired
    private CsvImportRowValidator validator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void shouldValidateAgainstRealAccountsAndTransactionsWithoutWrites() {
        Long activeAccountId = insertAccount(
                "W3D4_ACTIVE",
                "ACTIVE",
                false
        );
        insertAccount(
                "W3D4_DISABLED",
                "DISABLED",
                false
        );
        insertAccount(
                "W3D4_DELETED",
                "DISABLED",
                true
        );
        insertTransaction(
                activeAccountId,
                "EXT-EXISTING",
                TransactionSource.CSV_IMPORT,
                false
        );
        insertTransaction(
                activeAccountId,
                "EXT-DELETED",
                TransactionSource.CSV_IMPORT,
                true
        );
        insertTransaction(
                activeAccountId,
                "EXT-MANUAL",
                TransactionSource.MANUAL,
                false
        );
        insertTransaction(
                activeAccountId,
                "EXT-CASE",
                TransactionSource.CSV_IMPORT,
                false
        );

        Map<String, Integer> countsBefore = relatedTableCounts();

        ImportFileValidationResult result = validator.validate(
                new ParsedImportFile(
                        "integration.csv",
                        "b".repeat(64),
                        2048,
                        List.of(
                                row(2, "w3d4_active", "EXT-NEW"),
                                row(3, "W3D4_DISABLED", "EXT-DISABLED"),
                                row(4, "W3D4_MISSING", "EXT-MISSING"),
                                row(5, "W3D4_DELETED", "EXT-ACCOUNT-DELETED"),
                                row(6, "W3D4_ACTIVE", "EXT-EXISTING"),
                                row(7, "W3D4_ACTIVE", "EXT-DELETED"),
                                row(8, "W3D4_ACTIVE", "EXT-MANUAL"),
                                row(9, "W3D4_ACTIVE", "ext-case"),
                                row(10, "W3D4_ACTIVE", "EXT-NEW")
                        )
                )
        );

        assertThat(result.validRows())
                .extracting(ValidatedImportRow::rowNumber)
                .containsExactly(2, 8, 9);
        assertThat(result.validRows())
                .extracting(ValidatedImportRow::source)
                .containsOnly(TransactionSource.CSV_IMPORT);
        assertThat(result.errors())
                .extracting(ImportRowValidationError::rowNumber)
                .containsExactly(3, 4, 5, 6, 7, 10);
        assertThat(result.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(
                        ImportRowErrorCode.ACCOUNT_NOT_ACTIVE,
                        ImportRowErrorCode.ACCOUNT_NOT_FOUND,
                        ImportRowErrorCode.ACCOUNT_NOT_FOUND,
                        ImportRowErrorCode.DUPLICATE_TRANSACTION,
                        ImportRowErrorCode.DUPLICATE_TRANSACTION,
                        ImportRowErrorCode.DUPLICATE_TRANSACTION_IN_FILE
                );
        assertThat(result.failedRowCount()).isEqualTo(6);
        assertThat(result.duplicateRowCount()).isEqualTo(3);
        assertThat(relatedTableCounts()).isEqualTo(countsBefore);
    }

    private ParsedCsvRow row(
            int rowNumber,
            String accountNo,
            String externalTransactionNo) {
        return new ParsedCsvRow(
                rowNumber,
                List.of(
                        accountNo,
                        externalTransactionNo,
                        "EXPENSE",
                        "12.50",
                        "2026-07-31 09:00:00",
                        "validation test"
                )
        );
    }

    private Long insertAccount(
            String accountNo,
            String status,
            boolean deleted) {
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
                VALUES (?, ?, 'BANK', 'CNY', ?, ?)
                """,
                accountNo,
                "Week 3 Day 4 " + accountNo,
                status,
                deleted
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE account_no = ?",
                Long.class,
                accountNo
        );
    }

    private void insertTransaction(
            Long accountId,
            String externalTransactionNo,
            TransactionSource source,
            boolean deleted) {
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
                VALUES (?, ?, 'EXPENSE', 12.50,
                        '2026-07-31 09:00:00',
                        'Week 3 Day 4 validation',
                        ?, ?)
                """,
                accountId,
                externalTransactionNo,
                source.name(),
                deleted
        );
    }

    private Map<String, Integer> relatedTableCounts() {
        return Map.of(
                "accounts",
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM accounts",
                        Integer.class
                ),
                "transactions",
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM transactions",
                        Integer.class
                ),
                "import_jobs",
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM import_jobs",
                        Integer.class
                ),
                "import_row_errors",
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM import_row_errors",
                        Integer.class
                )
        );
    }

    private void clean() {
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
