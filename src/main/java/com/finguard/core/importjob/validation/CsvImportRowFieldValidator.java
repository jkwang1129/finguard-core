package com.finguard.core.importjob.validation;

import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.ParsedCsvRow;
import com.finguard.core.transaction.model.TransactionDirection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class CsvImportRowFieldValidator {

    private static final int EXPECTED_COLUMN_COUNT = 6;
    private static final int MAX_EXTERNAL_TRANSACTION_NO_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MAX_FUTURE_MINUTES = 5;
    private static final Pattern ACCOUNT_NO_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9_-]{2,63}$");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "^(0|[1-9][0-9]{0,16})(\\.[0-9]{1,2})?$"
    );
    private static final DateTimeFormatter TRANSACTION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    private final Clock businessClock;

    public CsvImportRowFieldValidator(Clock businessClock) {
        this.businessClock = Objects.requireNonNull(
                businessClock,
                "businessClock must not be null"
        );
    }

    LocalImportRowValidationResult validate(ParsedCsvRow row) {
        Objects.requireNonNull(row, "row must not be null");
        if (row.values().size() != EXPECTED_COLUMN_COUNT) {
            return invalid(
                    row.rowNumber(),
                    error(
                            row.rowNumber(),
                            "row",
                            ImportRowErrorCode.COLUMN_COUNT_MISMATCH,
                            Integer.toString(row.values().size()),
                            "CSV record must contain exactly 6 fields"
                    )
            );
        }

        List<ImportRowValidationError> errors = new ArrayList<>();
        String accountNo = validateAccountNo(row, errors);
        String externalTransactionNo =
                validateExternalTransactionNo(row, errors);
        TransactionDirection direction = validateDirection(row, errors);
        BigDecimal amount = validateAmount(row, errors);
        LocalDateTime transactionTime =
                validateTransactionTime(row, errors);
        String description = validateDescription(row, errors);

        if (!errors.isEmpty()) {
            return new LocalImportRowValidationResult(
                    row.rowNumber(),
                    null,
                    errors
            );
        }

        return new LocalImportRowValidationResult(
                row.rowNumber(),
                new LocallyValidatedImportRow(
                        row.rowNumber(),
                        accountNo,
                        externalTransactionNo,
                        direction,
                        amount,
                        transactionTime,
                        description
                ),
                List.of()
        );
    }

    private String validateAccountNo(
            ParsedCsvRow row,
            List<ImportRowValidationError> errors) {
        String rawValue = row.values().get(0);
        String normalizedValue =
                rawValue.trim().toUpperCase(Locale.ROOT);
        if (!ACCOUNT_NO_PATTERN.matcher(normalizedValue).matches()) {
            errors.add(error(
                    row.rowNumber(),
                    "account_no",
                    ImportRowErrorCode.INVALID_ACCOUNT_NO,
                    rawValue,
                    "Account number format is invalid"
            ));
            return null;
        }
        return normalizedValue;
    }

    private String validateExternalTransactionNo(
            ParsedCsvRow row,
            List<ImportRowValidationError> errors) {
        String rawValue = row.values().get(1);
        String normalizedValue = rawValue.trim();
        if (normalizedValue.isEmpty()
                || normalizedValue.length()
                > MAX_EXTERNAL_TRANSACTION_NO_LENGTH) {
            errors.add(error(
                    row.rowNumber(),
                    "external_transaction_no",
                    ImportRowErrorCode.INVALID_EXTERNAL_TRANSACTION_NO,
                    rawValue,
                    "External transaction number must contain 1 to 128 characters"
            ));
            return null;
        }
        return normalizedValue;
    }

    private TransactionDirection validateDirection(
            ParsedCsvRow row,
            List<ImportRowValidationError> errors) {
        String rawValue = row.values().get(2);
        String normalizedValue =
                rawValue.trim().toUpperCase(Locale.ROOT);
        try {
            return TransactionDirection.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            errors.add(error(
                    row.rowNumber(),
                    "direction",
                    ImportRowErrorCode.INVALID_DIRECTION,
                    rawValue,
                    "Direction must be INCOME or EXPENSE"
            ));
            return null;
        }
    }

    private BigDecimal validateAmount(
            ParsedCsvRow row,
            List<ImportRowValidationError> errors) {
        String rawValue = row.values().get(3);
        String normalizedValue = rawValue.trim();
        if (!AMOUNT_PATTERN.matcher(normalizedValue).matches()) {
            errors.add(invalidAmount(row.rowNumber(), rawValue));
            return null;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(normalizedValue);
        } catch (NumberFormatException exception) {
            errors.add(invalidAmount(row.rowNumber(), rawValue));
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(invalidAmount(row.rowNumber(), rawValue));
            return null;
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private LocalDateTime validateTransactionTime(
            ParsedCsvRow row,
            List<ImportRowValidationError> errors) {
        String rawValue = row.values().get(4);
        String normalizedValue = rawValue.trim();
        LocalDateTime transactionTime;
        try {
            transactionTime = LocalDateTime.parse(
                    normalizedValue,
                    TRANSACTION_TIME_FORMATTER
            );
        } catch (DateTimeParseException exception) {
            errors.add(invalidTransactionTime(
                    row.rowNumber(),
                    rawValue
            ));
            return null;
        }

        LocalDateTime latestAllowed =
                LocalDateTime.now(businessClock)
                        .plusMinutes(MAX_FUTURE_MINUTES);
        if (transactionTime.isAfter(latestAllowed)) {
            errors.add(invalidTransactionTime(
                    row.rowNumber(),
                    rawValue
            ));
            return null;
        }
        return transactionTime;
    }

    private String validateDescription(
            ParsedCsvRow row,
            List<ImportRowValidationError> errors) {
        String rawValue = row.values().get(5);
        String normalizedValue = rawValue.trim();
        if (normalizedValue.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(error(
                    row.rowNumber(),
                    "description",
                    ImportRowErrorCode.DESCRIPTION_TOO_LONG,
                    rawValue,
                    "Description must not exceed 255 characters"
            ));
            return null;
        }
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private ImportRowValidationError invalidAmount(
            int rowNumber,
            String rejectedValue) {
        return error(
                rowNumber,
                "amount",
                ImportRowErrorCode.INVALID_AMOUNT,
                rejectedValue,
                "Amount format or value is invalid"
        );
    }

    private ImportRowValidationError invalidTransactionTime(
            int rowNumber,
            String rejectedValue) {
        return error(
                rowNumber,
                "transaction_time",
                ImportRowErrorCode.INVALID_TRANSACTION_TIME,
                rejectedValue,
                "Transaction time format or value is invalid"
        );
    }

    private LocalImportRowValidationResult invalid(
            int rowNumber,
            ImportRowValidationError validationError) {
        return new LocalImportRowValidationResult(
                rowNumber,
                null,
                List.of(validationError)
        );
    }

    private ImportRowValidationError error(
            int rowNumber,
            String fieldName,
            ImportRowErrorCode errorCode,
            String rejectedValue,
            String message) {
        return ImportRowValidationError.of(
                rowNumber,
                fieldName,
                errorCode,
                rejectedValue,
                message
        );
    }
}
