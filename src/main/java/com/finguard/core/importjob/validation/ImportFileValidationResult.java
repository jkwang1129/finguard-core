package com.finguard.core.importjob.validation;

import com.finguard.core.importjob.model.ImportRowErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ImportFileValidationResult(
        int totalRows,
        List<ValidatedImportRow> validRows,
        List<ImportRowValidationError> errors) {

    public ImportFileValidationResult {
        if (totalRows < 1) {
            throw new IllegalArgumentException("totalRows must be positive");
        }
        Objects.requireNonNull(validRows, "validRows must not be null");
        Objects.requireNonNull(errors, "errors must not be null");

        validRows = List.copyOf(validRows);
        errors = List.copyOf(errors);

        Set<Integer> validRowNumbers = new HashSet<>();
        for (ValidatedImportRow validRow : validRows) {
            if (!validRowNumbers.add(validRow.rowNumber())) {
                throw new IllegalArgumentException(
                        "valid row numbers must be unique"
                );
            }
        }

        Set<Integer> failedRowNumbers = new HashSet<>();
        for (ImportRowValidationError error : errors) {
            if (validRowNumbers.contains(error.rowNumber())) {
                throw new IllegalArgumentException(
                        "a row cannot be both valid and failed"
                );
            }
            failedRowNumbers.add(error.rowNumber());
        }

        if (validRows.size() + failedRowNumbers.size() != totalRows) {
            throw new IllegalArgumentException(
                    "every input row must be valid or failed"
            );
        }
    }

    public int failedRowCount() {
        return (int) errors.stream()
                .map(ImportRowValidationError::rowNumber)
                .distinct()
                .count();
    }

    public int duplicateRowCount() {
        return (int) errors.stream()
                .filter(error ->
                        error.errorCode()
                                == ImportRowErrorCode
                                .DUPLICATE_TRANSACTION_IN_FILE
                                || error.errorCode()
                                == ImportRowErrorCode.DUPLICATE_TRANSACTION
                )
                .map(ImportRowValidationError::rowNumber)
                .distinct()
                .count();
    }
}
