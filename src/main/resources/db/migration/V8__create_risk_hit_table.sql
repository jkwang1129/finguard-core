CREATE TABLE risk_hits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reconciliation_result_id BIGINT NOT NULL,
    rule_code VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    observed_amount DECIMAL(19, 2) NULL,
    threshold_amount DECIMAL(19, 2) NULL,
    observed_count INT UNSIGNED NULL,
    threshold_count INT UNSIGNED NULL,
    window_seconds INT UNSIGNED NULL,
    reason_summary VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_risk_hits PRIMARY KEY (id),
    CONSTRAINT uk_risk_hits_result_rule
        UNIQUE (reconciliation_result_id, rule_code),
    CONSTRAINT fk_risk_hits_reconciliation_result
        FOREIGN KEY (reconciliation_result_id)
        REFERENCES reconciliation_results (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_risk_hits_rule
        CHECK (rule_code IN (
            'LARGE_AMOUNT',
            'POSSIBLE_DUPLICATE',
            'FREQUENT_TRANSACTION'
        )),
    CONSTRAINT chk_risk_hits_reason
        CHECK (reason_code IN (
            'AMOUNT_AT_OR_ABOVE_THRESHOLD',
            'SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME',
            'EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD'
        )),
    CONSTRAINT chk_risk_hits_values_positive
        CHECK (
            (observed_amount IS NULL OR observed_amount > 0)
            AND (threshold_amount IS NULL OR threshold_amount > 0)
            AND (observed_count IS NULL OR observed_count > 0)
            AND (threshold_count IS NULL OR threshold_count > 0)
            AND (window_seconds IS NULL OR window_seconds > 0)
        ),
    CONSTRAINT chk_risk_hits_summary
        CHECK (CHAR_LENGTH(TRIM(reason_summary)) > 0),
    CONSTRAINT chk_risk_hits_shape
        CHECK (
            (
                rule_code = 'LARGE_AMOUNT'
                AND reason_code = 'AMOUNT_AT_OR_ABOVE_THRESHOLD'
                AND observed_amount IS NOT NULL
                AND threshold_amount IS NOT NULL
                AND observed_amount >= threshold_amount
                AND observed_count IS NULL
                AND threshold_count IS NULL
                AND window_seconds IS NULL
            )
            OR (
                rule_code = 'POSSIBLE_DUPLICATE'
                AND reason_code =
                    'SAME_ACCOUNT_DIRECTION_AMOUNT_NEAR_TIME'
                AND observed_amount IS NULL
                AND threshold_amount IS NULL
                AND observed_count IS NOT NULL
                AND threshold_count = 1
                AND observed_count >= threshold_count
                AND window_seconds IS NOT NULL
            )
            OR (
                rule_code = 'FREQUENT_TRANSACTION'
                AND reason_code =
                    'EXPENSE_COUNT_AT_OR_ABOVE_THRESHOLD'
                AND observed_amount IS NULL
                AND threshold_amount IS NULL
                AND observed_count IS NOT NULL
                AND threshold_count IS NOT NULL
                AND observed_count >= threshold_count
                AND window_seconds IS NOT NULL
            )
        ),

    INDEX idx_risk_hits_rule_created_id (
        rule_code,
        created_at DESC,
        id DESC
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
