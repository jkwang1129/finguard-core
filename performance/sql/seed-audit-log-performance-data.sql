SET @d5perf_user_id = (
    SELECT MIN(u.id)
    FROM users u
    JOIN user_roles ur ON ur.user_id = u.id
    JOIN roles r ON r.id = ur.role_id
    WHERE u.status = 'ACTIVE'
      AND r.role_code = 'ADMIN'
);

DROP TEMPORARY TABLE IF EXISTS d5perf_seed_guard;
CREATE TEMPORARY TABLE d5perf_seed_guard (
    valid_value TINYINT NOT NULL,
    CONSTRAINT chk_d5perf_seed_guard CHECK (valid_value = 1)
);

INSERT INTO d5perf_seed_guard (valid_value)
VALUES (IF(@d5perf_user_id IS NOT NULL, 1, 0));

INSERT INTO d5perf_seed_guard (valid_value)
SELECT IF(COUNT(*) = 0, 1, 0)
FROM import_jobs
WHERE original_file_name LIKE 'D5PERF\_%';

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
    error_summary,
    created_by,
    started_at,
    finished_at,
    created_at,
    updated_at
)
SELECT CONCAT('D5PERF_', LPAD(sequence_number, 5, '0'), '.csv'),
       LOWER(SHA2(CONCAT('D5PERF_', sequence_number), 256)),
       1,
       'SUCCESS',
       0,
       0,
       0,
       0,
       NULL,
       NULL,
       @d5perf_user_id,
       TIMESTAMP('2026-01-01 00:00:00')
           + INTERVAL sequence_number SECOND,
       TIMESTAMP('2026-01-01 00:00:00')
           + INTERVAL sequence_number SECOND,
       TIMESTAMP('2026-01-01 00:00:00')
           + INTERVAL sequence_number SECOND,
       TIMESTAMP('2026-01-01 00:00:00')
           + INTERVAL sequence_number SECOND
FROM (
    SELECT ones.n
         + tens.n * 10
         + hundreds.n * 100
         + thousands.n * 1000
         + ten_thousands.n * 10000
         + 1 AS sequence_number
    FROM (
        SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) ones
    CROSS JOIN (
        SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) tens
    CROSS JOIN (
        SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) hundreds
    CROSS JOIN (
        SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) thousands
    CROSS JOIN (
        SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
        SELECT 3 UNION ALL SELECT 4
    ) ten_thousands
) generated_numbers;

INSERT INTO audit_logs (
    action_code,
    actor_type,
    actor_user_id,
    initiated_by,
    outcome,
    import_job_id,
    reconciliation_job_id,
    review_task_id,
    summary,
    created_at
)
SELECT 'CSV_UPLOAD_ACCEPTED',
       'USER',
       created_by,
       created_by,
       'SUCCESS',
       id,
       NULL,
       NULL,
       'Day 5 synthetic performance fixture',
       created_at
FROM import_jobs
WHERE original_file_name LIKE 'D5PERF\_%';

SELECT COUNT(*) AS d5perf_import_jobs
FROM import_jobs
WHERE original_file_name LIKE 'D5PERF\_%';

SELECT COUNT(*) AS d5perf_audit_logs
FROM audit_logs al
JOIN import_jobs ij ON ij.id = al.import_job_id
WHERE ij.original_file_name LIKE 'D5PERF\_%';
