package com.finguard.core.common.exception;

public record FieldValidationError(
        String field,
        String message
) {
}
