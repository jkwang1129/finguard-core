package com.finguard.core.importjob.exception;

import com.finguard.core.importjob.model.ImportFileRequestErrorCode;

import java.util.Objects;

public class InvalidImportFileRequestException extends RuntimeException {

    private final ImportFileRequestErrorCode errorCode;

    public InvalidImportFileRequestException(
            ImportFileRequestErrorCode errorCode,
            String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );
    }

    public ImportFileRequestErrorCode getErrorCode() {
        return errorCode;
    }
}
