package com.finguard.core.common.exception;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        ErrorCode code,
        String message,
        String path,
        List<FieldValidationError> fieldErrors
) {

    public ApiErrorResponse {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(
                fieldErrors,
                "fieldErrors must not be null"
        );
        fieldErrors = List.copyOf(fieldErrors);
    }
}
