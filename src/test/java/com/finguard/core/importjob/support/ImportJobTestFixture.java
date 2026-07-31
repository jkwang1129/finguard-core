package com.finguard.core.importjob.support;

import com.finguard.core.auth.model.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;

public class ImportJobTestFixture {

    public static final String USERNAME_PREFIX = "week3-day2-test-";

    private static final String TEST_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final JdbcTemplate jdbcTemplate;

    public ImportJobTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insertUser(String suffix) {
        String username = USERNAME_PREFIX + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO users (username, password_hash, status)
                VALUES (?, ?, ?)
                """,
                username,
                TEST_PASSWORD_HASH,
                UserStatus.ACTIVE.name()
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
    }

    public void clean() {
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
                DELETE ij
                FROM import_jobs ij
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
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

    public CleanupCounts cleanupCounts() {
        Integer rowErrors = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_row_errors ire
                INNER JOIN import_jobs ij ON ij.id = ire.import_job_id
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                Integer.class,
                USERNAME_PREFIX + "%"
        );
        Integer jobs = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_jobs ij
                INNER JOIN users u ON u.id = ij.created_by
                WHERE u.username LIKE ?
                """,
                Integer.class,
                USERNAME_PREFIX + "%"
        );
        Integer users = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username LIKE ?",
                Integer.class,
                USERNAME_PREFIX + "%"
        );
        return new CleanupCounts(rowErrors, jobs, users);
    }

    public record CleanupCounts(int rowErrors, int jobs, int users) {
    }
}
