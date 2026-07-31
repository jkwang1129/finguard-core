package com.finguard.core.importjob.validation;

import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.ParsedCsvRow;
import com.finguard.core.transaction.model.TransactionDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvImportRowFieldValidatorTest {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-31T02:00:00Z"),
            BUSINESS_ZONE
    );

    private CsvImportRowFieldValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CsvImportRowFieldValidator(FIXED_CLOCK);
    }

    @Test
    void shouldNormalizeEveryValidField() {
        ParsedCsvRow row = row(
                2,
                " bank-001 ",
                " Ext-001 ",
                " expense ",
                "1.2",
                "2026-07-31 10:05:00",
                "  Lunch  "
        );

        LocalImportRowValidationResult result = validator.validate(row);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRow()).isEqualTo(
                new LocallyValidatedImportRow(
                        2,
                        "BANK-001",
                        "Ext-001",
                        TransactionDirection.EXPENSE,
                        new BigDecimal("1.20"),
                        LocalDateTime.of(2026, 7, 31, 10, 5),
                        "Lunch"
                )
        );
    }

    @Test
    void shouldConvertBlankDescriptionToNull() {
        LocalImportRowValidationResult result = validator.validate(row(
                2,
                "BANK-001",
                "EXT-001",
                "INCOME",
                "20",
                "2026-07-31 09:59:59",
                "   "
        ));

        assertThat(result.validRow().description()).isNull();
        assertThat(result.validRow().amount())
                .isEqualByComparingTo("20.00");
        assertThat(result.validRow().amount().scale()).isEqualTo(2);
    }

    @Test
    void shouldAcceptExactUpperBoundValues() {
        String accountNo = "A" + "_".repeat(63);
        String externalTransactionNo = "E".repeat(128);
        String description = "d".repeat(255);

        LocalImportRowValidationResult result = validator.validate(row(
                2,
                accountNo,
                externalTransactionNo,
                "income",
                "99999999999999999.99",
                "2026-07-31 10:05:00",
                description
        ));

        assertThat(result.isValid()).isTrue();
        assertThat(result.validRow().accountNo()).hasSize(64);
        assertThat(result.validRow().externalTransactionNo())
                .hasSize(128);
        assertThat(result.validRow().amount())
                .isEqualByComparingTo("99999999999999999.99");
        assertThat(result.validRow().description()).hasSize(255);
    }

    @Test
    void shouldRejectTextValuesBeyondUpperBounds() {
        LocalImportRowValidationResult result = validator.validate(row(
                2,
                "A".repeat(65),
                "E".repeat(129),
                "INCOME",
                "0.01",
                "2026-07-31 10:00:00",
                "d".repeat(256)
        ));

        assertThat(result.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(
                        ImportRowErrorCode.INVALID_ACCOUNT_NO,
                        ImportRowErrorCode.INVALID_EXTERNAL_TRANSACTION_NO,
                        ImportRowErrorCode.DESCRIPTION_TOO_LONG
                );
    }

    @Test
    void shouldReturnOnlyColumnCountErrorForWrongColumnCount() {
        LocalImportRowValidationResult result = validator.validate(
                new ParsedCsvRow(3, List.of("", "", "", "", ""))
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly(
                ImportRowValidationError.of(
                        3,
                        "row",
                        ImportRowErrorCode.COLUMN_COUNT_MISMATCH,
                        "5",
                        "CSV record must contain exactly 6 fields"
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1.00",
            "+1.00",
            "1e3",
            "1,000.00",
            "1.234",
            "0001.00",
            "100000000000000000.00"
    })
    void shouldRejectAmountOutsideStrictCsvFormat(String amount) {
        LocalImportRowValidationResult result = validator.validate(row(
                2,
                "BANK-001",
                "EXT-001",
                "INCOME",
                amount,
                "2026-07-31 10:00:00",
                ""
        ));

        assertThat(result.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(ImportRowErrorCode.INVALID_AMOUNT);
    }

    @Test
    void shouldRejectInvalidCalendarDateAndTimeBeyondFiveMinutes() {
        LocalImportRowValidationResult invalidDate = validator.validate(row(
                2,
                "BANK-001",
                "EXT-001",
                "INCOME",
                "1.00",
                "2026-02-29 10:00:00",
                ""
        ));
        LocalImportRowValidationResult future = validator.validate(row(
                3,
                "BANK-001",
                "EXT-002",
                "INCOME",
                "1.00",
                "2026-07-31 10:05:01",
                ""
        ));

        assertThat(invalidDate.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(
                        ImportRowErrorCode.INVALID_TRANSACTION_TIME
                );
        assertThat(future.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(
                        ImportRowErrorCode.INVALID_TRANSACTION_TIME
                );
    }

    @Test
    void shouldCollectIndependentErrorsInStableFieldOrder() {
        String longDescription = "x".repeat(256);
        LocalImportRowValidationResult result = validator.validate(row(
                7,
                "a!",
                "   ",
                "OUT",
                "0",
                "not-a-time",
                longDescription
        ));

        assertThat(result.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(
                        ImportRowErrorCode.INVALID_ACCOUNT_NO,
                        ImportRowErrorCode.INVALID_EXTERNAL_TRANSACTION_NO,
                        ImportRowErrorCode.INVALID_DIRECTION,
                        ImportRowErrorCode.INVALID_AMOUNT,
                        ImportRowErrorCode.INVALID_TRANSACTION_TIME,
                        ImportRowErrorCode.DESCRIPTION_TOO_LONG
                );
        assertThat(result.errors())
                .extracting(ImportRowValidationError::fieldName)
                .containsExactly(
                        "account_no",
                        "external_transaction_no",
                        "direction",
                        "amount",
                        "transaction_time",
                        "description"
                );
        assertThat(result.errors().get(5).rejectedValue())
                .hasSize(255)
                .endsWith("...");
    }

    private ParsedCsvRow row(
            int rowNumber,
            String accountNo,
            String externalTransactionNo,
            String direction,
            String amount,
            String transactionTime,
            String description) {
        return new ParsedCsvRow(
                rowNumber,
                List.of(
                        accountNo,
                        externalTransactionNo,
                        direction,
                        amount,
                        transactionTime,
                        description
                )
        );
    }
}
