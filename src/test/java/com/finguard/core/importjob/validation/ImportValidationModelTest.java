package com.finguard.core.importjob.validation;

import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportValidationModelTest {

    @Test
    void shouldCopyListsAndCountFailedAndDuplicateRows() {
        List<ValidatedImportRow> validRows = new ArrayList<>();
        validRows.add(validRow(2));
        List<ImportRowValidationError> errors = new ArrayList<>();
        errors.add(error(
                3,
                ImportRowErrorCode.INVALID_AMOUNT
        ));
        errors.add(error(
                3,
                ImportRowErrorCode.INVALID_DIRECTION
        ));
        errors.add(error(
                4,
                ImportRowErrorCode.DUPLICATE_TRANSACTION
        ));

        ImportFileValidationResult result =
                new ImportFileValidationResult(3, validRows, errors);
        validRows.clear();
        errors.clear();

        assertThat(result.validRows()).hasSize(1);
        assertThat(result.errors()).hasSize(3);
        assertThat(result.failedRowCount()).isEqualTo(2);
        assertThat(result.duplicateRowCount()).isEqualTo(1);
        assertThatThrownBy(() -> result.validRows().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.errors().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectARecordThatIsBothValidAndFailed() {
        assertThatThrownBy(() -> new ImportFileValidationResult(
                1,
                List.of(validRow(2)),
                List.of(error(
                        2,
                        ImportRowErrorCode.INVALID_AMOUNT
                ))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both valid and failed");
    }

    private ValidatedImportRow validRow(int rowNumber) {
        return new ValidatedImportRow(
                rowNumber,
                10L,
                "EXT-" + rowNumber,
                TransactionDirection.INCOME,
                new BigDecimal("1.00"),
                LocalDateTime.of(2026, 7, 31, 10, 0),
                null,
                TransactionSource.CSV_IMPORT
        );
    }

    private ImportRowValidationError error(
            int rowNumber,
            ImportRowErrorCode errorCode) {
        return ImportRowValidationError.of(
                rowNumber,
                "amount",
                errorCode,
                "bad",
                "Safe validation error"
        );
    }
}
