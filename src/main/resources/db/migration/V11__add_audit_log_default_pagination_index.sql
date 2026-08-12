ALTER TABLE audit_logs
    ADD INDEX idx_audit_logs_created_id (
        created_at DESC,
        id DESC
    );
