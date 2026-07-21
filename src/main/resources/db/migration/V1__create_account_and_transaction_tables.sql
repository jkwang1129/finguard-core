CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_no VARCHAR(64) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uk_accounts_account_no UNIQUE (account_no),
    CONSTRAINT chk_accounts_type
        CHECK (account_type IN ('BANK', 'CASH', 'PAYMENT_PLATFORM')),
    CONSTRAINT chk_accounts_currency
        CHECK (currency = 'CNY'),
    CONSTRAINT chk_accounts_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_accounts_deleted
        CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_accounts_deleted_status
        CHECK (deleted = 0 OR status = 'DISABLED')
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    external_transaction_no VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    transaction_time DATETIME(3) NOT NULL,
    description VARCHAR(255) NULL,
    source VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uk_transactions_account_source_external_no
        UNIQUE (account_id, source, external_transaction_no),
    CONSTRAINT chk_transactions_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_transactions_direction
        CHECK (direction IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_transactions_source
        CHECK (source IN ('MANUAL', 'CSV_IMPORT')),
    CONSTRAINT chk_transactions_deleted
        CHECK (deleted IN (0, 1)),

    INDEX idx_transactions_account_time (account_id, transaction_time)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
