CREATE TABLE review_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_type VARCHAR(32) NOT NULL,
    reconciliation_result_id BIGINT NULL,
    risk_hit_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    decision_note VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_review_tasks PRIMARY KEY (id),
    CONSTRAINT uk_review_tasks_reconciliation_result
        UNIQUE (reconciliation_result_id),
    CONSTRAINT uk_review_tasks_risk_hit
        UNIQUE (risk_hit_id),
    CONSTRAINT fk_review_tasks_reconciliation_result
        FOREIGN KEY (reconciliation_result_id)
        REFERENCES reconciliation_results (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_review_tasks_risk_hit
        FOREIGN KEY (risk_hit_id)
        REFERENCES risk_hits (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_review_tasks_reviewer
        FOREIGN KEY (reviewed_by)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_review_tasks_source_type
        CHECK (source_type IN (
            'RECONCILIATION_EXCEPTION',
            'RISK_HIT'
        )),
    CONSTRAINT chk_review_tasks_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'IGNORED')),
    CONSTRAINT chk_review_tasks_source
        CHECK (
            (
                source_type = 'RECONCILIATION_EXCEPTION'
                AND reconciliation_result_id IS NOT NULL
                AND risk_hit_id IS NULL
            )
            OR (
                source_type = 'RISK_HIT'
                AND reconciliation_result_id IS NULL
                AND risk_hit_id IS NOT NULL
            )
        ),
    CONSTRAINT chk_review_tasks_state
        CHECK (
            (
                status = 'PENDING'
                AND version = 0
                AND reviewed_by IS NULL
                AND reviewed_at IS NULL
                AND decision_note IS NULL
            )
            OR (
                status IN ('CONFIRMED', 'IGNORED')
                AND version = 1
                AND reviewed_by IS NOT NULL
                AND reviewed_at IS NOT NULL
            )
        ),

    INDEX idx_review_tasks_status_created_id (
        status,
        created_at DESC,
        id DESC
    ),
    INDEX idx_review_tasks_source_status (
        source_type,
        status,
        created_at DESC,
        id DESC
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
