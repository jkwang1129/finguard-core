package com.finguard.core.importjob.validation;

import com.finguard.core.importjob.model.ImportRowErrorCode;

import java.util.Objects;
import java.util.Set;

public record ImportRowValidationError(
        int rowNumber,
        String fieldName,
        ImportRowErrorCode errorCode,
        String rejectedValue,
        String message) {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final int TRUNCATED_PREFIX_LENGTH = 252;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "row",
            "account_no",
            "external_transaction_no",
            "direction",
            "amount",
            "transaction_time",
            "description"
    );

    public ImportRowValidationError {
        if (rowNumber < 2) {
            throw new IllegalArgumentException(
                    "rowNumber must identify a data record"
            );
        }
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(message, "message must not be null");

        if (!ALLOWED_FIELDS.contains(fieldName)) {
            throw new IllegalArgumentException(
                    "fieldName is not supported: " + fieldName
            );
        }
        if (rejectedValue != null
                && rejectedValue.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "rejectedValue must not exceed 255 characters"
            );
        }
        if (message.isBlank() || message.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "message must contain between 1 and 255 characters"
            );
        }
    }

    public static ImportRowValidationError of(
            int rowNumber,
            String fieldName,
            ImportRowErrorCode errorCode,
            String rejectedValue,
            String message) {
        return new ImportRowValidationError(
                rowNumber,
                fieldName,
                errorCode,
                truncateRejectedValue(rejectedValue),
                message
        );
    }

    static String truncateRejectedValue(String rejectedValue) {
        if (rejectedValue == null
                || rejectedValue.length() <= MAX_TEXT_LENGTH) {
            return rejectedValue;
        }
        return rejectedValue.substring(0, TRUNCATED_PREFIX_LENGTH) + "...";
    }
}
