package com.finguard.core.importjob.exception;

import com.finguard.core.importjob.model.ImportFileErrorCode;

import java.util.Objects;

public class ImportFileParseException extends RuntimeException {

    private final ImportFileErrorCode errorCode;

    public ImportFileParseException(
            ImportFileErrorCode errorCode,
            String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );
    }

    public ImportFileErrorCode getErrorCode() {
        return errorCode;
    }
}
