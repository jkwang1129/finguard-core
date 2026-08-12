SELECT COUNT(*) AS total_audit_logs
FROM audit_logs;

EXPLAIN ANALYZE
SELECT id,
       action_code AS actionCode,
       actor_type AS actorType,
       actor_user_id AS actorUserId,
       initiated_by AS initiatedBy,
       outcome,
       import_job_id AS importJobId,
       reconciliation_job_id AS reconciliationJobId,
       review_task_id AS reviewTaskId,
       summary,
       created_at AS createdAt
FROM audit_logs
ORDER BY created_at DESC, id DESC
LIMIT 20;
