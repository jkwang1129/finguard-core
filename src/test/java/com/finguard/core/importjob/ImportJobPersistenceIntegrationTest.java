package com.finguard.core.importjob;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.importjob.entity.ImportJob;
import com.finguard.core.importjob.entity.ImportRowError;
import com.finguard.core.importjob.mapper.ImportJobMapper;
import com.finguard.core.importjob.mapper.ImportRowErrorMapper;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.support.ImportJobTestFixture;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ImportJobPersistenceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ImportJobMapper importJobMapper;

    @Autowired
    private ImportRowErrorMapper importRowErrorMapper;

    private JdbcTemplate jdbcTemplate;
    private ImportJobTestFixture fixture;
    private Long ownerId;
    private int hashSequence;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        fixture = new ImportJobTestFixture(jdbcTemplate);
        fixture.clean();
        ownerId = fixture.insertUser(UUID.randomUUID().toString());
        hashSequence = 1;
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
        assertThat(fixture.cleanupCounts())
                .isEqualTo(new ImportJobTestFixture.CleanupCounts(0, 0, 0));
    }

    @Test
    void flywayShouldCreateImportTablesConstraintsAndPaginationIndex() {
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion()).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("8");

        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('import_jobs', 'import_row_errors')
                """,
                Integer.class
        );
        assertThat(tableCount).isEqualTo(2);

        List<String> checkConstraints = jdbcTemplate.queryForList(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name IN ('import_jobs', 'import_row_errors')
                  AND constraint_type = 'CHECK'
                """,
                String.class
        );
        assertThat(checkConstraints).containsExactlyInAnyOrder(
                "chk_import_jobs_file_name",
                "chk_import_jobs_file_hash",
                "chk_import_jobs_file_size",
                "chk_import_jobs_status",
                "chk_import_jobs_row_totals",
                "chk_import_jobs_duplicate_rows",
                "chk_import_jobs_terminal_rows",
                "chk_import_jobs_file_error_code",
                "chk_import_jobs_file_error_status",
                "chk_import_row_errors_csv_row_number",
                "chk_import_row_errors_field",
                "chk_import_row_errors_error_code",
                "chk_import_row_errors_message"
        );

        List<String> restrictForeignKeys = jdbcTemplate.queryForList(
                """
                SELECT CONCAT(table_name, ':', constraint_name, ':', delete_rule)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN (
                      'fk_import_jobs_created_by',
                      'fk_import_row_errors_job'
                  )
                """,
                String.class
        );
        assertThat(restrictForeignKeys).containsExactlyInAnyOrder(
                "import_jobs:fk_import_jobs_created_by:RESTRICT",
                "import_row_errors:fk_import_row_errors_job:RESTRICT"
        );

        List<String> paginationIndex = jdbcTemplate.queryForList(
                """
                SELECT CONCAT(column_name, ':', collation)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'import_row_errors'
                  AND index_name = 'idx_import_row_errors_job_row_id'
                ORDER BY seq_in_index
                """,
                String.class
        );
        assertThat(paginationIndex).containsExactly(
                "import_job_id:A",
                "csv_row_number:A",
                "id:A"
        );

        String fileHashCollation = jdbcTemplate.queryForObject(
                """
                SELECT collation_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'import_jobs'
                  AND column_name = 'file_hash'
                """,
                String.class
        );
        assertThat(fileHashCollation).isEqualTo("ascii_bin");
    }

    @Test
    void importJobMapperShouldRoundTripEveryStatusAndFileErrorCode() {
        for (ImportJobStatus status : ImportJobStatus.values()) {
            ImportJob job = newJob(status);
            assertThat(importJobMapper.insert(job)).isEqualTo(1);
            assertThat(job.getId()).isNotNull();

            ImportJob byId = importJobMapper.selectById(job.getId());
            ImportJob byHash = importJobMapper.findByFileHash(job.getFileHash());

            assertThat(byId.getStatus()).isEqualTo(status);
            assertThat(byId.getOriginalFileName()).isEqualTo(job.getOriginalFileName());
            assertThat(byId.getCreatedAt()).isNotNull();
            assertThat(byHash.getId()).isEqualTo(job.getId());
            assertThat(byHash.getFileHash()).isEqualTo(job.getFileHash());
        }

        for (ImportFileErrorCode errorCode : ImportFileErrorCode.values()) {
            ImportJob failedJob = newJob(ImportJobStatus.FAILED);
            failedJob.setFileErrorCode(errorCode);
            failedJob.setErrorSummary("Safe file failure summary");

            assertThat(importJobMapper.insert(failedJob)).isEqualTo(1);
            assertThat(importJobMapper.selectById(failedJob.getId()).getFileErrorCode())
                    .isEqualTo(errorCode);
        }
    }

    @Test
    void rowErrorMapperShouldRoundTripEveryCodeAndAllowMultipleErrorsPerRow() {
        ImportJob job = insertJob(ImportJobStatus.PENDING);

        for (ImportRowErrorCode errorCode : ImportRowErrorCode.values()) {
            ImportRowError rowError = newRowError(
                    job.getId(),
                    2,
                    fieldFor(errorCode),
                    errorCode
            );
            assertThat(importRowErrorMapper.insert(rowError)).isEqualTo(1);
            assertThat(rowError.getId()).isNotNull();
        }

        IPage<ImportRowError> page = importRowErrorMapper.selectPageByImportJobId(
                new Page<>(1, 100),
                job.getId()
        );

        assertThat(page.getTotal()).isEqualTo(ImportRowErrorCode.values().length);
        assertThat(page.getRecords())
                .extracting(ImportRowError::getErrorCode)
                .containsExactly(ImportRowErrorCode.values());
        assertThat(page.getRecords())
                .extracting(ImportRowError::getRowNumber)
                .containsOnly(2);
        assertThat(page.getRecords())
                .allSatisfy(error -> {
                    assertThat(error.getImportJobId()).isEqualTo(job.getId());
                    assertThat(error.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void databaseShouldRejectDuplicateHashAndInvalidStaticValues() {
        ImportJob first = insertJob(ImportJobStatus.PENDING);
        ImportJob duplicateHash = newJob(ImportJobStatus.PENDING);
        duplicateHash.setFileHash(first.getFileHash());

        assertThatThrownBy(() -> importJobMapper.insert(duplicateHash))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertRawJobRejected(
                "bad-hash",
                1024L,
                "PENDING",
                0,
                0,
                0,
                0,
                null,
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                0L,
                "PENDING",
                0,
                0,
                0,
                0,
                null,
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "UNKNOWN",
                0,
                0,
                0,
                0,
                null,
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "FAILED",
                0,
                0,
                0,
                0,
                "UNKNOWN_ERROR",
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "PROCESSING",
                1,
                1,
                1,
                0,
                null,
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "PROCESSING",
                1,
                0,
                1,
                2,
                null,
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "SUCCESS",
                2,
                1,
                0,
                0,
                null,
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "PENDING",
                0,
                0,
                0,
                0,
                "INVALID_HEADER",
                ownerId
        );
        assertRawJobRejected(
                nextHash(),
                1024L,
                "PENDING",
                0,
                0,
                0,
                0,
                null,
                Long.MAX_VALUE
        );
    }

    @Test
    void foreignKeysShouldRestrictDeletingReferencedUserAndJob() {
        ImportJob job = insertJob(ImportJobStatus.PENDING);
        ImportRowError rowError = newRowError(
                job.getId(),
                2,
                "amount",
                ImportRowErrorCode.INVALID_AMOUNT
        );
        importRowErrorMapper.insert(rowError);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                ownerId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM import_jobs WHERE id = ?",
                job.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseShouldRejectInvalidRowErrorValues() {
        ImportJob job = insertJob(ImportJobStatus.PENDING);

        assertRawRowErrorRejected(
                job.getId(),
                1,
                "amount",
                "INVALID_AMOUNT",
                "Invalid amount"
        );
        assertRawRowErrorRejected(
                job.getId(),
                2,
                "unknown_field",
                "INVALID_AMOUNT",
                "Invalid amount"
        );
        assertRawRowErrorRejected(
                job.getId(),
                2,
                "amount",
                "UNKNOWN_ERROR",
                "Invalid amount"
        );
        assertRawRowErrorRejected(
                job.getId(),
                2,
                "amount",
                "INVALID_AMOUNT",
                " "
        );
        assertRawRowErrorRejected(
                Long.MAX_VALUE,
                2,
                "amount",
                "INVALID_AMOUNT",
                "Invalid amount"
        );
    }

    @Test
    void rowErrorPaginationShouldBeStableAcrossPages() {
        ImportJob job = insertJob(ImportJobStatus.PENDING);
        List<ImportRowError> inserted = new ArrayList<>();

        for (int rowNumber : List.of(5, 2, 3, 2, 4)) {
            ImportRowError rowError = newRowError(
                    job.getId(),
                    rowNumber,
                    "amount",
                    ImportRowErrorCode.INVALID_AMOUNT
            );
            importRowErrorMapper.insert(rowError);
            inserted.add(rowError);
        }

        IPage<ImportRowError> firstPage = importRowErrorMapper
                .selectPageByImportJobId(new Page<>(1, 2), job.getId());
        IPage<ImportRowError> secondPage = importRowErrorMapper
                .selectPageByImportJobId(new Page<>(2, 2), job.getId());
        IPage<ImportRowError> thirdPage = importRowErrorMapper
                .selectPageByImportJobId(new Page<>(3, 2), job.getId());

        List<ImportRowError> combined = new ArrayList<>();
        combined.addAll(firstPage.getRecords());
        combined.addAll(secondPage.getRecords());
        combined.addAll(thirdPage.getRecords());

        List<Long> expectedIds = inserted.stream()
                .sorted(Comparator.comparing(ImportRowError::getRowNumber)
                        .thenComparing(ImportRowError::getId))
                .map(ImportRowError::getId)
                .toList();

        assertThat(firstPage.getTotal()).isEqualTo(5);
        assertThat(firstPage.getPages()).isEqualTo(3);
        assertThat(combined)
                .extracting(ImportRowError::getRowNumber)
                .containsExactly(2, 2, 3, 4, 5);
        assertThat(combined)
                .extracting(ImportRowError::getId)
                .containsExactlyElementsOf(expectedIds)
                .doesNotHaveDuplicates();
    }

    private ImportJob insertJob(ImportJobStatus status) {
        ImportJob job = newJob(status);
        assertThat(importJobMapper.insert(job)).isEqualTo(1);
        return job;
    }

    private ImportJob newJob(ImportJobStatus status) {
        ImportJob job = new ImportJob();
        job.setOriginalFileName("week3-day2-test-" + hashSequence + ".csv");
        job.setFileHash(nextHash());
        job.setFileSizeBytes(1024L);
        job.setStatus(status);
        job.setTotalRows(0);
        job.setSuccessRows(0);
        job.setFailedRows(0);
        job.setDuplicateRows(0);
        job.setCreatedBy(ownerId);

        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case PENDING -> {
            }
            case PROCESSING -> job.setStartedAt(now);
            case SUCCESS -> {
                job.setTotalRows(1);
                job.setSuccessRows(1);
                job.setStartedAt(now.minusSeconds(1));
                job.setFinishedAt(now);
            }
            case PARTIAL_SUCCESS -> {
                job.setTotalRows(2);
                job.setSuccessRows(1);
                job.setFailedRows(1);
                job.setStartedAt(now.minusSeconds(1));
                job.setFinishedAt(now);
            }
            case FAILED -> {
                job.setFileErrorCode(ImportFileErrorCode.INVALID_HEADER);
                job.setErrorSummary("Invalid CSV header");
                job.setStartedAt(now.minusSeconds(1));
                job.setFinishedAt(now);
            }
        }
        return job;
    }

    private ImportRowError newRowError(
            Long importJobId,
            int rowNumber,
            String field,
            ImportRowErrorCode errorCode) {
        ImportRowError rowError = new ImportRowError();
        rowError.setImportJobId(importJobId);
        rowError.setRowNumber(rowNumber);
        rowError.setField(field);
        rowError.setErrorCode(errorCode);
        rowError.setRejectedValue("rejected");
        rowError.setMessage("Safe row validation message");
        return rowError;
    }

    private String fieldFor(ImportRowErrorCode errorCode) {
        return switch (errorCode) {
            case COLUMN_COUNT_MISMATCH -> "row";
            case INVALID_ACCOUNT_NO, ACCOUNT_NOT_FOUND, ACCOUNT_NOT_ACTIVE ->
                    "account_no";
            case INVALID_EXTERNAL_TRANSACTION_NO,
                 DUPLICATE_TRANSACTION_IN_FILE,
                 DUPLICATE_TRANSACTION -> "external_transaction_no";
            case INVALID_DIRECTION -> "direction";
            case INVALID_AMOUNT -> "amount";
            case INVALID_TRANSACTION_TIME -> "transaction_time";
            case DESCRIPTION_TOO_LONG -> "description";
        };
    }

    private void assertRawJobRejected(
            String fileHash,
            long fileSizeBytes,
            String status,
            int totalRows,
            int successRows,
            int failedRows,
            int duplicateRows,
            String fileErrorCode,
            long createdBy) {
        assertThatThrownBy(() -> jdbcTemplate.update(
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
                    file_error_code,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "week3-day2-test-invalid.csv",
                fileHash,
                fileSizeBytes,
                status,
                totalRows,
                successRows,
                failedRows,
                duplicateRows,
                fileErrorCode,
                createdBy
        )).isInstanceOf(DataAccessException.class);
    }

    private void assertRawRowErrorRejected(
            long importJobId,
            int rowNumber,
            String field,
            String errorCode,
            String message) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO import_row_errors (
                    import_job_id,
                    csv_row_number,
                    field_name,
                    error_code,
                    rejected_value,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                importJobId,
                rowNumber,
                field,
                errorCode,
                "rejected",
                message
        )).isInstanceOf(DataAccessException.class);
    }

    private String nextHash() {
        return String.format(Locale.ROOT, "%064x", hashSequence++);
    }
}
