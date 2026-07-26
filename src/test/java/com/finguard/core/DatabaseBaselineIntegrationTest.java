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
        assertThat(current.getVersion().getVersion()).isEqualTo("3");
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
                  AND table_name IN (?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, "accounts");
            statement.setString(2, "transactions");
            statement.setString(3, "users");
            statement.setString(4, "roles");
            statement.setString(5, "user_roles");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(5);
            }
        }
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
