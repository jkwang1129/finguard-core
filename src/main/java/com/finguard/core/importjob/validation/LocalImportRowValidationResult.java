package com.finguard.core.importjob.validation;

import java.util.List;
import java.util.Objects;

record LocalImportRowValidationResult(
        int rowNumber,
        LocallyValidatedImportRow validRow,
        List<ImportRowValidationError> errors) {

    LocalImportRowValidationResult {
        if (rowNumber < 2) {
            throw new IllegalArgumentException(
                    "rowNumber must identify a data record"
            );
        }
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);

        if ((validRow == null) == errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "result must contain either a valid row or errors"
            );
        }
    }

    boolean isValid() {
        return validRow != null;
    }
}
