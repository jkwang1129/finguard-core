package com.finguard.core.reconciliation.support;

import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReconciliationTestFixture {

    public static final String USERNAME_PREFIX = "week3-day6-test-";
    public static final String ACCOUNT_PREFIX = "W3D6_";

    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insertUser() {
        String username = USERNAME_PREFIX + token();
        jdbcTemplate.update(
                """
                INSERT INTO users (username, password_hash, status)
                VALUES (?, ?, 'ACTIVE')
                """,
                username,
                PASSWORD_HASH
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
    }

    public Long insertAccount() {
        String accountNo = ACCOUNT_PREFIX + token().substring(0, 16);
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
                VALUES (?, 'Week 3 Day 6', 'BANK', 'CNY', 'ACTIVE', 0)
                """,
                accountNo
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE account_no = ?",
                Long.class,
                accountNo
        );
    }

    public Long insertImportJob(
            Long ownerId,
            ImportJobStatus status,
            int successRows) {
        String token = token();
        String hash = token + token;
        int totalRows = successRows;
        jdbcTemplate.update(
                """
                INSERT INTO import_jobs (
                    original_file_name,
                    file_hash,
                    file_size_bytes,
                    status,
                    total_rows,
                    success_rows,
                    failed_rows,
                    duplicate_rows,
                    created_by,
                    started_at,
                    finished_at
                )
                VALUES (?, ?, 1, ?, ?, ?, 0, 0, ?,
                        '2026-07-31 08:00:00',
                        '2026-07-31 08:01:00')
                """,
                "reconciliation-" + token + ".csv",
                hash,
                status.name(),
                totalRows,
                successRows,
                ownerId
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM import_jobs WHERE file_hash = ?",
                Long.class,
                hash
        );
    }

    public Long insertTransaction(
            Long accountId,
            Long importJobId,
            String externalNo,
            TransactionDirection direction,
            String amount,
            LocalDateTime time,
            TransactionSource source) {
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    account_id,
                    import_job_id,
                    external_transaction_no,
                    direction,
                    amount,
                    transaction_time,
                    description,
                    source,
                    deleted
                )
                VALUES (?, ?, ?, ?, ?, ?, 'Week 3 Day 6', ?, 0)
                """,
                accountId,
                importJobId,
                externalNo,
                direction.name(),
                new BigDecimal(amount),
                time,
                source.name()
        );
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM transactions
                WHERE account_id = ?
                  AND source = ?
                  AND external_transaction_no = ?
                """,
                Long.class,
                accountId,
                source.name(),
                externalNo
        );
    }

    public void clean() {
        jdbcTemplate.update(
                """
                DELETE rr
                FROM reconciliation_results rr
                INNER JOIN reconciliation_jobs rj
                    ON rj.id = rr.reconciliation_job_id
                INNER JOIN users u ON u.id = rj.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE oe
                FROM outbox_events oe
                INNER JOIN reconciliation_jobs rj
                    ON oe.event_type = 'RECONCILIATION_REQUESTED'
                   AND oe.aggregate_id = rj.id
                INNER JOIN users u ON u.id = rj.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE rj
                FROM reconciliation_jobs rj
                INNER JOIN users u ON u.id = rj.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE ire
                FROM import_row_errors ire
                INNER JOIN import_jobs ij ON ij.id = ire.import_job_id
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
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
                """
                DELETE oe
                FROM outbox_events oe
                INNER JOIN import_jobs ij
                    ON oe.event_type = 'IMPORT_REQUESTED'
                   AND oe.aggregate_id = ij.id
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE ijf
                FROM import_job_files ijf
                INNER JOIN import_jobs ij ON ij.id = ijf.import_job_id
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE ij
                FROM import_jobs ij
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM accounts WHERE account_no LIKE ?",
                ACCOUNT_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE ur
                FROM user_roles ur
                INNER JOIN users u ON u.id = ur.user_id
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE username LIKE ?",
                USERNAME_PREFIX + "%"
        );
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
