ALTER TABLE transactions
    ADD COLUMN import_job_id BIGINT NULL AFTER account_id,
    ADD CONSTRAINT fk_transactions_import_job
        FOREIGN KEY (import_job_id)
        REFERENCES import_jobs (id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT chk_transactions_import_source
        CHECK (
            (source = 'MANUAL' AND import_job_id IS NULL)
            OR (source = 'CSV_IMPORT' AND import_job_id IS NOT NULL)
        );
