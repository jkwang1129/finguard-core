package com.finguard.core.auth.bootstrap;

import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.support.AuthTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthBootstrapIntegrationTest {

    private static final String ADMIN_PASSWORD =
            "Admin-Bootstrap-Password";
    private static final String REVIEWER_PASSWORD =
            "Reviewer-Bootstrap-Password";

    @Autowired
    private AuthBootstrapRunner runner;

    @Autowired
    private AuthBootstrapProperties properties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;
    private AuthTestFixture fixture;
    private String adminUsername;
    private String reviewerUsername;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        fixture = new AuthTestFixture(jdbcTemplate);
        ensureFixedRolesExist();
        fixture.clean();
        resetProperties();

        adminUsername = fixture.username("bootstrap-admin");
        reviewerUsername =
                fixture.username("bootstrap-reviewer");
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
        ensureFixedRolesExist();
        resetProperties();
    }

    @Test
    void disabledByDefaultLeavesAuthenticationDataUnchanged() {
        assertThat(properties.isEnabled()).isFalse();

        runner.run(null);

        assertThat(testUserCount()).isZero();
        assertThat(testBindingCount()).isZero();
    }

    @Test
    void createsNormalizedUsersWithBcryptAndCorrectRoles() {
        enableCompleteBootstrap();
        properties.getAdmin().setUsername(
                "  " + adminUsername.toUpperCase() + "  "
        );

        runner.run(null);

        List<Map<String, Object>> users = jdbcTemplate
                .queryForList(
                        """
                        SELECT username, password_hash, status
                        FROM users
                        WHERE username IN (?, ?)
                        ORDER BY username
                        """,
                        adminUsername,
                        reviewerUsername
                );
        assertThat(users).hasSize(2);
        assertThat(users)
                .extracting(row -> row.get("status"))
                .containsOnly(UserStatus.ACTIVE.name());

        String adminHash = passwordHash(adminUsername);
        String reviewerHash = passwordHash(reviewerUsername);
        assertThat(adminHash)
                .startsWith("$2")
                .isNotEqualTo(ADMIN_PASSWORD);
        assertThat(reviewerHash)
                .startsWith("$2")
                .isNotEqualTo(REVIEWER_PASSWORD);
        assertThat(passwordEncoder.matches(
                ADMIN_PASSWORD,
                adminHash
        )).isTrue();
        assertThat(passwordEncoder.matches(
                REVIEWER_PASSWORD,
                reviewerHash
        )).isTrue();

        assertThat(rolesFor(adminUsername))
                .containsExactly("ADMIN");
        assertThat(rolesFor(reviewerUsername))
                .containsExactly("REVIEWER");
    }

    @Test
    void repeatedExecutionKeepsIdsHashesAndBindingsUnchanged() {
        enableCompleteBootstrap();
        runner.run(null);

        Long originalAdminId = fixture.userId(adminUsername);
        Long originalReviewerId =
                fixture.userId(reviewerUsername);
        String originalAdminHash =
                passwordHash(adminUsername);
        String originalReviewerHash =
                passwordHash(reviewerUsername);

        properties.getAdmin().setPassword(
                "Different-Admin-Password"
        );
        properties.getReviewer().setPassword(
                "Different-Reviewer-Password"
        );
        runner.run(null);

        assertThat(testUserCount()).isEqualTo(2);
        assertThat(testBindingCount()).isEqualTo(2);
        assertThat(fixture.userId(adminUsername))
                .isEqualTo(originalAdminId);
        assertThat(fixture.userId(reviewerUsername))
                .isEqualTo(originalReviewerId);
        assertThat(passwordHash(adminUsername))
                .isEqualTo(originalAdminHash);
        assertThat(passwordHash(reviewerUsername))
                .isEqualTo(originalReviewerHash);
        assertThat(passwordEncoder.matches(
                "Different-Admin-Password",
                originalAdminHash
        )).isFalse();
    }

    @Test
    void missingConfigurationFailsBeforeCreatingAnything() {
        enableCompleteBootstrap();
        String configuredPassword =
                properties.getAdmin().getPassword();
        properties.getReviewer().setPassword(null);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "REVIEWER bootstrap username and password "
                                + "must be configured"
                )
                .hasMessageNotContaining(configuredPassword)
                .hasMessageNotContaining(adminUsername);
        assertThat(testUserCount()).isZero();
        assertThat(testBindingCount()).isZero();
    }

    @Test
    void roleLookupFailureRollsBackEarlierUserAndBinding() {
        enableCompleteBootstrap();
        int deletedRoles = jdbcTemplate.update(
                "DELETE FROM roles WHERE role_code = 'REVIEWER'"
        );
        assertThat(deletedRoles).isEqualTo(1);

        try {
            assertThatThrownBy(() -> runner.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                            "Required bootstrap role is unavailable: "
                                    + "REVIEWER"
                    );
            assertThat(testUserCount()).isZero();
            assertThat(testBindingCount()).isZero();
        } finally {
            ensureFixedRolesExist();
        }
    }

    private void enableCompleteBootstrap() {
        properties.setEnabled(true);
        properties.getAdmin().setUsername(adminUsername);
        properties.getAdmin().setPassword(ADMIN_PASSWORD);
        properties.getReviewer().setUsername(
                reviewerUsername
        );
        properties.getReviewer().setPassword(
                REVIEWER_PASSWORD
        );
    }

    private void resetProperties() {
        properties.setEnabled(false);
        properties.getAdmin().setUsername(null);
        properties.getAdmin().setPassword(null);
        properties.getReviewer().setUsername(null);
        properties.getReviewer().setPassword(null);
    }

    private String passwordHash(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?",
                String.class,
                username
        );
    }

    private List<String> rolesFor(String username) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.role_code
                FROM roles r
                INNER JOIN user_roles ur ON ur.role_id = r.id
                INNER JOIN users u ON u.id = ur.user_id
                WHERE u.username = ?
                ORDER BY r.role_code
                """,
                String.class,
                username
        );
    }

    private int testUserCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE username LIKE 'day2-test-%'
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int testBindingCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_roles ur
                INNER JOIN users u ON u.id = ur.user_id
                WHERE u.username LIKE 'day2-test-%'
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void ensureFixedRolesExist() {
        Integer reviewerCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM roles
                WHERE role_code = 'REVIEWER'
                """,
                Integer.class
        );
        if (reviewerCount != null && reviewerCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO roles (role_code) VALUES ('REVIEWER')"
            );
        }
    }
}
