package com.finguard.core.auth.support;

import com.finguard.core.auth.model.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;

public class AuthTestFixture {

    public static final String TEST_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String USERNAME_PREFIX = "day2-test-";

    private final JdbcTemplate jdbcTemplate;

    public AuthTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String username(String suffix) {
        return USERNAME_PREFIX + suffix;
    }

    public Long insertUser(String username, UserStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO users (username, password_hash, status)
                VALUES (?, ?, ?)
                """,
                username,
                TEST_PASSWORD_HASH,
                status.name()
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
    }

    public Long roleId(String roleCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE role_code = ?",
                Long.class,
                roleCode
        );
    }

    public void bindRole(Long userId, Long roleId) {
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                userId,
                roleId
        );
    }

    public void clean() {
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
}
