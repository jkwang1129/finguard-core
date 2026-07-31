CREATE TABLE reconciliation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_job_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_count INT UNSIGNED NOT NULL DEFAULT 0,
    matched_count INT UNSIGNED NOT NULL DEFAULT 0,
    unmatched_count INT UNSIGNED NOT NULL DEFAULT 0,
    duplicate_count INT UNSIGNED NOT NULL DEFAULT 0,
    suspicious_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_summary VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_reconciliation_jobs PRIMARY KEY (id),
    CONSTRAINT uk_reconciliation_jobs_import_job UNIQUE (import_job_id),
    CONSTRAINT fk_reconciliation_jobs_import_job
        FOREIGN KEY (import_job_id)
        REFERENCES import_jobs (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reconciliation_jobs_created_by
        FOREIGN KEY (created_by)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_reconciliation_jobs_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED'
        )),
    CONSTRAINT chk_reconciliation_jobs_counts
        CHECK (
            matched_count + unmatched_count
            + duplicate_count + suspicious_count <= total_count
        ),
    CONSTRAINT chk_reconciliation_jobs_completed_counts
        CHECK (
            status <> 'COMPLETED'
            OR matched_count + unmatched_count
               + duplicate_count + suspicious_count = total_count
        ),
    CONSTRAINT chk_reconciliation_jobs_error
        CHECK (
            (status = 'FAILED' AND error_summary IS NOT NULL)
            OR (status <> 'FAILED' AND error_summary IS NULL)
        ),
    CONSTRAINT chk_reconciliation_jobs_times
        CHECK (
            (status = 'PENDING'
                AND started_at IS NULL
                AND finished_at IS NULL)
            OR (status = 'PROCESSING'
                AND started_at IS NOT NULL
                AND finished_at IS NULL)
            OR (status IN ('COMPLETED', 'FAILED')
                AND started_at IS NOT NULL
                AND finished_at IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE reconciliation_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reconciliation_job_id BIGINT NOT NULL,
    csv_transaction_id BIGINT NOT NULL,
    manual_transaction_id BIGINT NULL,
    result_type VARCHAR(16) NOT NULL,
    match_method VARCHAR(16) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_reconciliation_results PRIMARY KEY (id),
    CONSTRAINT uk_reconciliation_results_job_csv
        UNIQUE (reconciliation_job_id, csv_transaction_id),
    CONSTRAINT fk_reconciliation_results_job
        FOREIGN KEY (reconciliation_job_id)
        REFERENCES reconciliation_jobs (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reconciliation_results_csv_transaction
        FOREIGN KEY (csv_transaction_id)
        REFERENCES transactions (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reconciliation_results_manual_transaction
        FOREIGN KEY (manual_transaction_id)
        REFERENCES transactions (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_reconciliation_results_type
        CHECK (result_type IN (
            'MATCHED',
            'UNMATCHED',
            'DUPLICATE',
            'SUSPICIOUS'
        )),
    CONSTRAINT chk_reconciliation_results_method
        CHECK (match_method IN ('EXACT', 'TOLERANCE', 'NONE')),
    CONSTRAINT chk_reconciliation_results_reason
        CHECK (reason_code IN (
            'EXACT_MATCH',
            'TOLERANCE_MATCH',
            'NO_CANDIDATE',
            'MULTIPLE_CANDIDATES',
            'MANUAL_ALREADY_MATCHED',
            'DIRECTION_MISMATCH',
            'AMOUNT_MISMATCH',
            'TIME_OUT_OF_RANGE'
        )),
    CONSTRAINT chk_reconciliation_results_shape
        CHECK (
            (
                result_type = 'MATCHED'
                AND manual_transaction_id IS NOT NULL
                AND (
                    (match_method = 'EXACT'
                        AND reason_code = 'EXACT_MATCH')
                    OR (match_method = 'TOLERANCE'
                        AND reason_code = 'TOLERANCE_MATCH')
                )
            )
            OR (
                result_type = 'UNMATCHED'
                AND manual_transaction_id IS NULL
                AND match_method = 'NONE'
                AND reason_code = 'NO_CANDIDATE'
            )
            OR (
                result_type = 'DUPLICATE'
                AND match_method = 'NONE'
                AND reason_code IN (
                    'MULTIPLE_CANDIDATES',
                    'MANUAL_ALREADY_MATCHED'
                )
            )
            OR (
                result_type = 'SUSPICIOUS'
                AND manual_transaction_id IS NOT NULL
                AND match_method = 'NONE'
                AND reason_code IN (
                    'DIRECTION_MISMATCH',
                    'AMOUNT_MISMATCH',
                    'TIME_OUT_OF_RANGE'
                )
            )
        ),

    INDEX idx_reconciliation_results_job_type_csv_id (
        reconciliation_job_id,
        result_type,
        csv_transaction_id,
        id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
