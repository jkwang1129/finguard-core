CREATE TABLE import_job_files (
    import_job_id BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    content_length INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_import_job_files PRIMARY KEY (import_job_id),
    CONSTRAINT fk_import_job_files_job
        FOREIGN KEY (import_job_id)
        REFERENCES import_jobs (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_import_job_files_length
        CHECK (
            content_length BETWEEN 1 AND 5242880
            AND OCTET_LENGTH(content) = content_length
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    schema_version INT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error_summary VARCHAR(255) NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_type_aggregate
        UNIQUE (event_type, aggregate_id),
    CONSTRAINT chk_outbox_events_type
        CHECK (event_type IN (
            'IMPORT_REQUESTED',
            'RECONCILIATION_REQUESTED'
        )),
    CONSTRAINT chk_outbox_events_aggregate
        CHECK (aggregate_id > 0),
    CONSTRAINT chk_outbox_events_schema
        CHECK (schema_version = 1),
    CONSTRAINT chk_outbox_events_status
        CHECK (status IN ('NEW', 'RETRY', 'SENT')),
    CONSTRAINT chk_outbox_events_state
        CHECK (
            (status = 'NEW'
                AND attempts = 0
                AND last_error_summary IS NULL
                AND sent_at IS NULL)
            OR (status = 'RETRY'
                AND attempts > 0
                AND last_error_summary IS NOT NULL
                AND sent_at IS NULL)
            OR (status = 'SENT'
                AND last_error_summary IS NULL
                AND sent_at IS NOT NULL)
        ),

    INDEX idx_outbox_events_due (
        status,
        next_attempt_at,
        id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
