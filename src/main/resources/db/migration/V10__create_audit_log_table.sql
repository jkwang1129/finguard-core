CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action_code VARCHAR(40) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_user_id BIGINT NULL,
    initiated_by BIGINT NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    import_job_id BIGINT NULL,
    reconciliation_job_id BIGINT NULL,
    review_task_id BIGINT NULL,
    summary VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL,

    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_audit_logs_action_import
        UNIQUE (action_code, import_job_id),
    CONSTRAINT uk_audit_logs_action_reconciliation
        UNIQUE (action_code, reconciliation_job_id),
    CONSTRAINT uk_audit_logs_action_review
        UNIQUE (action_code, review_task_id),

    CONSTRAINT fk_audit_logs_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_initiated_by
        FOREIGN KEY (initiated_by)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_import_job
        FOREIGN KEY (import_job_id)
        REFERENCES import_jobs (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_reconciliation_job
        FOREIGN KEY (reconciliation_job_id)
        REFERENCES reconciliation_jobs (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_review_task
        FOREIGN KEY (review_task_id)
        REFERENCES review_tasks (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_audit_logs_action
        CHECK (action_code IN (
            'CSV_UPLOAD_ACCEPTED',
            'IMPORT_FAILED',
            'RECONCILIATION_COMPLETED',
            'REVIEW_CONFIRMED',
            'REVIEW_IGNORED'
        )),
    CONSTRAINT chk_audit_logs_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_audit_logs_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILED')),
    CONSTRAINT chk_audit_logs_actor
        CHECK (
            (
                actor_type = 'USER'
                AND actor_user_id IS NOT NULL
                AND actor_user_id = initiated_by
            )
            OR (
                actor_type = 'SYSTEM'
                AND actor_user_id IS NULL
            )
        ),
    CONSTRAINT chk_audit_logs_target
        CHECK (
            (
                action_code IN (
                    'CSV_UPLOAD_ACCEPTED',
                    'IMPORT_FAILED'
                )
                AND import_job_id IS NOT NULL
                AND reconciliation_job_id IS NULL
                AND review_task_id IS NULL
            )
            OR (
                action_code = 'RECONCILIATION_COMPLETED'
                AND import_job_id IS NULL
                AND reconciliation_job_id IS NOT NULL
                AND review_task_id IS NULL
            )
            OR (
                action_code IN (
                    'REVIEW_CONFIRMED',
                    'REVIEW_IGNORED'
                )
                AND import_job_id IS NULL
                AND reconciliation_job_id IS NULL
                AND review_task_id IS NOT NULL
            )
        ),
    CONSTRAINT chk_audit_logs_action_outcome
        CHECK (
            (action_code = 'IMPORT_FAILED' AND outcome = 'FAILED')
            OR (action_code <> 'IMPORT_FAILED' AND outcome = 'SUCCESS')
        ),
    CONSTRAINT chk_audit_logs_summary
        CHECK (CHAR_LENGTH(TRIM(summary)) BETWEEN 1 AND 255),

    INDEX idx_audit_logs_action_created_id (
        action_code,
        created_at DESC,
        id DESC
    ),
    INDEX idx_audit_logs_initiated_created_id (
        initiated_by,
        created_at DESC,
        id DESC
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
