package com.finguard.core;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseBaselineIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void dataSourceShouldConnectToConfiguredDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
             ResultSet resultSet = statement.executeQuery()) {

            assertThat(connection.isValid(2)).isTrue();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isNotBlank();
        }
    }

    @Test
    void flywayShouldHaveAppliedLatestVersion() {
        MigrationInfo current = flyway.info().current();

        assertThat(current).isNotNull();
        assertThat(current.getVersion()).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("9");
    }

    @Test
    void myBatisShouldCreateSqlSessionFactory() {
        assertThat(sqlSessionFactory).isNotNull();
        assertThat(sqlSessionFactory.getConfiguration()).isNotNull();
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment()).isNotNull();
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource()).isNotNull();
    }

    @Test
    void requiredBusinessTablesShouldExist() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, "accounts");
            statement.setString(2, "transactions");
            statement.setString(3, "users");
            statement.setString(4, "roles");
            statement.setString(5, "user_roles");
            statement.setString(6, "import_jobs");
            statement.setString(7, "import_row_errors");
            statement.setString(8, "reconciliation_jobs");
            statement.setString(9, "reconciliation_results");
            statement.setString(10, "import_job_files");
            statement.setString(11, "outbox_events");
            statement.setString(12, "risk_hits");
            statement.setString(13, "review_tasks");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(13);
            }
        }
    }

    @Test
    void reviewSchemaShouldEnforceSourceStateAndIdempotency() {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForList(
                """
                SELECT constraint_name, delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN (
                      'fk_review_tasks_reconciliation_result',
                      'fk_review_tasks_risk_hit',
                      'fk_review_tasks_reviewer'
                  )
                ORDER BY constraint_name
                """
        )).hasSize(3)
                .allSatisfy(row -> assertThat(row.get("delete_rule"))
                        .isEqualTo("RESTRICT"));
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'review_tasks'
                  AND constraint_type = 'CHECK'
                  AND constraint_name IN (
                      'chk_review_tasks_source_type',
                      'chk_review_tasks_status',
                      'chk_review_tasks_source',
                      'chk_review_tasks_state'
                  )
                """,
                Integer.class
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_tasks'
                  AND index_name IN (
                      'uk_review_tasks_reconciliation_result',
                      'uk_review_tasks_risk_hit'
                  )
                  AND non_unique = 0
                """,
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_tasks'
                  AND index_name IN (
                      'idx_review_tasks_status_created_id',
                      'idx_review_tasks_source_status'
                  )
                """,
                Integer.class
        )).isEqualTo(2);
    }

    @Test
    void riskSchemaShouldEnforceShapeTraceabilityAndIdempotency() {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name =
                      'fk_risk_hits_reconciliation_result'
                """,
                String.class
        )).isEqualTo("RESTRICT");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'risk_hits'
                  AND index_name = 'uk_risk_hits_result_rule'
                  AND non_unique = 0
                """,
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'risk_hits'
                  AND index_name = 'idx_risk_hits_rule_created_id'
                """,
                Integer.class
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'risk_hits'
                  AND constraint_type = 'CHECK'
                  AND constraint_name IN (
                      'chk_risk_hits_rule',
                      'chk_risk_hits_reason',
                      'chk_risk_hits_values_positive',
                      'chk_risk_hits_summary',
                      'chk_risk_hits_shape'
                  )
                """,
                Integer.class
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'risk_hits'
                  AND column_name = 'observed_amount'
                """
        )).containsEntry("NUMERIC_PRECISION", 19L)
                .containsEntry("NUMERIC_SCALE", 2L);
    }

    @Test
    void outboxSchemaShouldEnforceFileAndEventInvariants() {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'fk_import_job_files_job'
                """,
                String.class
        )).isEqualTo("RESTRICT");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'outbox_events'
                  AND index_name = 'uk_outbox_events_type_aggregate'
                  AND non_unique = 0
                """,
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'outbox_events'
                  AND index_name = 'idx_outbox_events_due'
                """,
                Integer.class
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'CHECK'
                  AND constraint_name IN (
                      'chk_import_job_files_length',
                      'chk_outbox_events_type',
                      'chk_outbox_events_schema',
                      'chk_outbox_events_status',
                      'chk_outbox_events_state'
                  )
                """,
                Integer.class
        )).isEqualTo(5);
    }

    @Test
    void transactionPaginationIndexShouldExist() throws SQLException {
        String sql = """
                SELECT column_name, collation
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'transactions'
                  AND index_name = 'idx_transactions_deleted_time_id'
                ORDER BY seq_in_index
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            assertIndexColumn(resultSet, "deleted", "A");
            assertIndexColumn(resultSet, "transaction_time", "D");
            assertIndexColumn(resultSet, "id", "D");
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void transactionImportJobLinkShouldExist() {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'transactions'
                  AND column_name = 'import_job_id'
                """,
                String.class
        )).isEqualTo("YES");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'fk_transactions_import_job'
                """,
                String.class
        )).isEqualTo("RESTRICT");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'transactions'
                  AND constraint_name =
                      'chk_transactions_import_source'
                  AND constraint_type = 'CHECK'
                """,
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void reconciliationSchemaShouldEnforceTraceabilityAndIdempotency() {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'reconciliation_jobs'
                  AND index_name =
                      'uk_reconciliation_jobs_import_job'
                  AND non_unique = 0
                """,
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'reconciliation_results'
                  AND index_name =
                      'uk_reconciliation_results_job_csv'
                  AND non_unique = 0
                """,
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name IN (
                      'reconciliation_jobs',
                      'reconciliation_results'
                  )
                  AND delete_rule = 'RESTRICT'
                """,
                Integer.class
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'CHECK'
                  AND constraint_name IN (
                      'chk_reconciliation_jobs_completed_counts',
                      'chk_reconciliation_jobs_error',
                      'chk_reconciliation_jobs_times',
                      'chk_reconciliation_results_shape'
                  )
                """,
                Integer.class
        )).isEqualTo(4);
    }

    private void assertIndexColumn(
            ResultSet resultSet,
            String expectedColumn,
            String expectedCollation) throws SQLException {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString("column_name"))
                .isEqualTo(expectedColumn);
        assertThat(resultSet.getString("collation"))
                .isEqualTo(expectedCollation);
    }
}
