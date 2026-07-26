CREATE INDEX idx_transactions_deleted_time_id
    ON transactions (deleted, transaction_time DESC, id DESC);
