DROP TEMPORARY TABLE IF EXISTS d5perf_cleanup_guard;
CREATE TEMPORARY TABLE d5perf_cleanup_guard (
    valid_value TINYINT NOT NULL,
    CONSTRAINT chk_d5perf_cleanup_guard CHECK (valid_value = 1)
);

INSERT INTO d5perf_cleanup_guard (valid_value)
SELECT IF(COUNT(*) = 0, 1, 0)
FROM audit_logs al
JOIN import_jobs ij ON ij.id = al.import_job_id
WHERE ij.original_file_name LIKE 'D5PERF\_%'
  AND (
      al.action_code <> 'CSV_UPLOAD_ACCEPTED'
      OR al.summary <> 'Day 5 synthetic performance fixture'
  );

DELETE al
FROM audit_logs al
JOIN import_jobs ij ON ij.id = al.import_job_id
WHERE ij.original_file_name LIKE 'D5PERF\_%'
  AND al.action_code = 'CSV_UPLOAD_ACCEPTED'
  AND al.summary = 'Day 5 synthetic performance fixture';

DELETE FROM import_jobs
WHERE original_file_name LIKE 'D5PERF\_%';

SELECT COUNT(*) AS remaining_d5perf_import_jobs
FROM import_jobs
WHERE original_file_name LIKE 'D5PERF\_%';

SELECT COUNT(*) AS remaining_d5perf_audit_logs
FROM audit_logs
WHERE summary = 'Day 5 synthetic performance fixture';
