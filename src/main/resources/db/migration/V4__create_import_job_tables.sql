CREATE TABLE import_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_file_name VARCHAR(255) NOT NULL,
    file_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    file_size_bytes BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT UNSIGNED NOT NULL DEFAULT 0,
    success_rows INT UNSIGNED NOT NULL DEFAULT 0,
    failed_rows INT UNSIGNED NOT NULL DEFAULT 0,
    duplicate_rows INT UNSIGNED NOT NULL DEFAULT 0,
    file_error_code VARCHAR(32) NULL,
    error_summary VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_import_jobs PRIMARY KEY (id),
    CONSTRAINT uk_import_jobs_file_hash UNIQUE (file_hash),
    CONSTRAINT fk_import_jobs_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_import_jobs_file_name
        CHECK (CHAR_LENGTH(TRIM(original_file_name)) BETWEEN 1 AND 255),
    CONSTRAINT chk_import_jobs_file_hash
        CHECK (file_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_import_jobs_file_size
        CHECK (file_size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT chk_import_jobs_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'SUCCESS',
            'PARTIAL_SUCCESS',
            'FAILED'
        )),
    CONSTRAINT chk_import_jobs_row_totals
        CHECK (success_rows + failed_rows <= total_rows),
    CONSTRAINT chk_import_jobs_duplicate_rows
        CHECK (duplicate_rows <= failed_rows),
    CONSTRAINT chk_import_jobs_terminal_rows
        CHECK (
            status IN ('PENDING', 'PROCESSING')
            OR total_rows = success_rows + failed_rows
        ),
    CONSTRAINT chk_import_jobs_file_error_code
        CHECK (
            file_error_code IS NULL
            OR file_error_code IN (
                'INVALID_UTF8',
                'INVALID_HEADER',
                'MALFORMED_CSV',
                'NO_DATA_ROWS',
                'TOO_MANY_ROWS',
                'PROCESSING_FAILED'
            )
        ),
    CONSTRAINT chk_import_jobs_file_error_status
        CHECK (file_error_code IS NULL OR status = 'FAILED')
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE import_row_errors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_job_id BIGINT NOT NULL,
    csv_row_number INT UNSIGNED NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    rejected_value VARCHAR(255) NULL,
    message VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_import_row_errors PRIMARY KEY (id),
    CONSTRAINT fk_import_row_errors_job
        FOREIGN KEY (import_job_id) REFERENCES import_jobs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_import_row_errors_csv_row_number
        CHECK (csv_row_number >= 2),
    CONSTRAINT chk_import_row_errors_field
        CHECK (field_name IN (
            'row',
            'account_no',
            'external_transaction_no',
            'direction',
            'amount',
            'transaction_time',
            'description'
        )),
    CONSTRAINT chk_import_row_errors_error_code
        CHECK (error_code IN (
            'COLUMN_COUNT_MISMATCH',
            'INVALID_ACCOUNT_NO',
            'ACCOUNT_NOT_FOUND',
            'ACCOUNT_NOT_ACTIVE',
            'INVALID_EXTERNAL_TRANSACTION_NO',
            'INVALID_DIRECTION',
            'INVALID_AMOUNT',
            'INVALID_TRANSACTION_TIME',
            'DESCRIPTION_TOO_LONG',
            'DUPLICATE_TRANSACTION_IN_FILE',
            'DUPLICATE_TRANSACTION'
        )),
    CONSTRAINT chk_import_row_errors_message
        CHECK (CHAR_LENGTH(TRIM(message)) BETWEEN 1 AND 255),

    INDEX idx_import_row_errors_job_row_id (
        import_job_id,
        csv_row_number,
        id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
