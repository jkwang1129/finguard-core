package com.finguard.core.auth;

import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.support.AuthTestFixture;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthDatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    private JdbcTemplate jdbcTemplate;
    private AuthTestFixture fixture;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        fixture = new AuthTestFixture(jdbcTemplate);
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void flywayShouldCreateAuthTablesAndSeedFixedRoles() {
        MigrationInfo current = flyway.info().current();

        assertThat(current).isNotNull();
        assertThat(current.getVersion()).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("4");

        Integer authTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('users', 'roles', 'user_roles')
                """,
                Integer.class
        );
        List<String> roleCodes = jdbcTemplate.queryForList(
                "SELECT role_code FROM roles ORDER BY role_code",
                String.class
        );
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users",
                Integer.class
        );

        assertThat(authTableCount).isEqualTo(3);
        assertThat(roleCodes).containsExactly("ADMIN", "REVIEWER");
        assertThat(userCount).isZero();
    }

    @Test
    void authSchemaShouldHaveRequiredReverseIndexAndRestrictForeignKeys() {
        Integer reverseIndexCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'user_roles'
                  AND index_name = 'idx_user_roles_role_id'
                  AND column_name = 'role_id'
                """,
                Integer.class
        );
        Integer restrictForeignKeyCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'user_roles'
                  AND constraint_name IN ('fk_user_roles_user', 'fk_user_roles_role')
                  AND delete_rule = 'RESTRICT'
                """,
                Integer.class
        );

        assertThat(reverseIndexCount).isEqualTo(1);
        assertThat(restrictForeignKeyCount).isEqualTo(2);
    }

    @Test
    void uniquenessAndValueDomainConstraintsShouldBeEnforced() {
        String username = fixture.username("constraints");
        fixture.insertUser(username, UserStatus.ACTIVE);

        assertThatThrownBy(() -> fixture.insertUser(username, UserStatus.DISABLED))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO users (username, password_hash, status)
                VALUES (?, ?, ?)
                """,
                fixture.username("invalid-status"),
                AuthTestFixture.TEST_PASSWORD_HASH,
                "LOCKED"
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO roles (role_code) VALUES (?)",
                "ADMIN"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO roles (role_code) VALUES (?)",
                "AUDITOR"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void duplicateBindingAndUnknownForeignKeysShouldBeRejected() {
        Long userId = fixture.insertUser(fixture.username("foreign-keys"), UserStatus.ACTIVE);
        Long adminRoleId = fixture.roleId("ADMIN");
        fixture.bindRole(userId, adminRoleId);

        assertThatThrownBy(() -> fixture.bindRole(userId, adminRoleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> fixture.bindRole(Long.MAX_VALUE, adminRoleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> fixture.bindRole(userId, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleBindingShouldRestrictDeletingReferencedUserAndRole() {
        Long userId = fixture.insertUser(fixture.username("delete-restrict"), UserStatus.ACTIVE);
        Long reviewerRoleId = fixture.roleId("REVIEWER");
        fixture.bindRole(userId, reviewerRoleId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM roles WHERE id = ?",
                reviewerRoleId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
