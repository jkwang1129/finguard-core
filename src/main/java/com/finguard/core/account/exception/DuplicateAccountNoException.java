package com.finguard.core.account.exception;

public class DuplicateAccountNoException extends RuntimeException {

    public DuplicateAccountNoException(String accountNo) {
        super("Account number already exists: " + accountNo);
    }
}
