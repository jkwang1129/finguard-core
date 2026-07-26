package com.finguard.core.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Invalid username or password";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
