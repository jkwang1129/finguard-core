package com.finguard.core.auth;

import com.finguard.core.FinGuardCoreApplication;
import com.finguard.core.auth.TransactionBoundaryIntegrationTest
        .RollbackProbeConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                FinGuardCoreApplication.class,
                RollbackProbeConfiguration.class
        }
)
class TransactionBoundaryIntegrationTest {

    private static final String USERNAME = "day6-tx-test-rollback";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RollbackProbe rollbackProbe;

    @BeforeEach
    void setUp() {
        cleanProbeData();
    }

    @AfterEach
    void tearDown() {
        cleanProbeData();
    }

    @Test
    void runtimeExceptionRollsBackUserAndRoleBindingTogether() {
        assertThat(AopUtils.isAopProxy(rollbackProbe)).isTrue();

        assertThatThrownBy(
                () -> rollbackProbe.insertUserAndRoleThenFail(USERNAME)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Deliberate rollback probe failure");

        assertThat(countUsers()).isZero();
        assertThat(countBindings()).isZero();
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                USERNAME
        );
    }

    private int countBindings() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_roles ur
                INNER JOIN users u ON u.id = ur.user_id
                WHERE u.username = ?
                """,
                Integer.class,
                USERNAME
        );
    }

    private void cleanProbeData() {
        jdbcTemplate.update(
                """
                DELETE ur
                FROM user_roles ur
                INNER JOIN users u ON u.id = ur.user_id
                WHERE u.username = ?
                """,
                USERNAME
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE username = ?",
                USERNAME
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackProbeConfiguration {

        @Bean
        RollbackProbe rollbackProbe(JdbcTemplate jdbcTemplate) {
            return new RollbackProbe(jdbcTemplate);
        }
    }

    static class RollbackProbe {

        private static final String TEST_PASSWORD_HASH =
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        private final JdbcTemplate jdbcTemplate;

        RollbackProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void insertUserAndRoleThenFail(String username) {
            if (!TransactionSynchronizationManager
                    .isActualTransactionActive()) {
                throw new IllegalStateException(
                        "Rollback probe requires an active transaction"
                );
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO users (username, password_hash, status)
                    VALUES (?, ?, 'ACTIVE')
                    """,
                    username,
                    TEST_PASSWORD_HASH
            );
            Long userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = ?",
                    Long.class,
                    username
            );
            Long roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM roles WHERE role_code = 'ADMIN'",
                    Long.class
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO user_roles (user_id, role_id)
                    VALUES (?, ?)
                    """,
                    userId,
                    roleId
            );

            throw new IllegalStateException(
                    "Deliberate rollback probe failure"
            );
        }
    }
}
