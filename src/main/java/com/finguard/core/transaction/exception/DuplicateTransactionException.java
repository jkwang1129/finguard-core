package com.finguard.core.transaction.exception;

import com.finguard.core.transaction.model.TransactionSource;

public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(
            Long accountId,
            TransactionSource source,
            String externalTransactionNo) {
        super(
                "Transaction already exists for account "
                        + accountId
                        + ", source "
                        + source
                        + " and external number "
                        + externalTransactionNo
        );
    }
}
