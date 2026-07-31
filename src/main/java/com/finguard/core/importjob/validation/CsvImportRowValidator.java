package com.finguard.core.importjob.validation;

import com.finguard.core.account.entity.Account;
import com.finguard.core.account.mapper.AccountMapper;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.ParsedImportFile;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionBusinessKey;
import com.finguard.core.transaction.model.TransactionSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CsvImportRowValidator {

    static final int QUERY_BATCH_SIZE = 500;
    private static final Map<String, Integer> FIELD_ORDER = Map.of(
            "row", 0,
            "account_no", 1,
            "external_transaction_no", 2,
            "direction", 3,
            "amount", 4,
            "transaction_time", 5,
            "description", 6
    );

    private final CsvImportRowFieldValidator fieldValidator;
    private final AccountMapper accountMapper;
    private final TransactionMapper transactionMapper;

    public CsvImportRowValidator(
            CsvImportRowFieldValidator fieldValidator,
            AccountMapper accountMapper,
            TransactionMapper transactionMapper) {
        this.fieldValidator = Objects.requireNonNull(
                fieldValidator,
                "fieldValidator must not be null"
        );
        this.accountMapper = Objects.requireNonNull(
                accountMapper,
                "accountMapper must not be null"
        );
        this.transactionMapper = Objects.requireNonNull(
                transactionMapper,
                "transactionMapper must not be null"
        );
    }

    @Transactional(readOnly = true)
    public ImportFileValidationResult validate(
            ParsedImportFile parsedFile) {
        Objects.requireNonNull(parsedFile, "parsedFile must not be null");

        List<ImportRowValidationError> errors = new ArrayList<>();
        List<LocallyValidatedImportRow> locallyValidRows =
                new ArrayList<>();
        parsedFile.rows().stream()
                .map(fieldValidator::validate)
                .forEach(result -> {
                    if (result.isValid()) {
                        locallyValidRows.add(result.validRow());
                    } else {
                        errors.addAll(result.errors());
                    }
                });

        Map<String, Account> accountsByNumber =
                loadAccountsByNumber(locallyValidRows);
        List<ResolvedImportRow> resolvedRows = new ArrayList<>();
        for (LocallyValidatedImportRow row : locallyValidRows) {
            Account account = accountsByNumber.get(row.accountNo());
            if (account == null) {
                errors.add(error(
                        row.rowNumber(),
                        "account_no",
                        ImportRowErrorCode.ACCOUNT_NOT_FOUND,
                        row.accountNo(),
                        "Account does not exist"
                ));
                continue;
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                errors.add(error(
                        row.rowNumber(),
                        "account_no",
                        ImportRowErrorCode.ACCOUNT_NOT_ACTIVE,
                        row.accountNo(),
                        "Account is not active"
                ));
                continue;
            }
            resolvedRows.add(new ResolvedImportRow(row, account.getId()));
        }

        Map<TransactionBusinessKey, ResolvedImportRow> firstRowsByKey =
                new LinkedHashMap<>();
        for (ResolvedImportRow row : resolvedRows) {
            ResolvedImportRow previous =
                    firstRowsByKey.putIfAbsent(row.businessKey(), row);
            if (previous != null) {
                errors.add(error(
                        row.localRow().rowNumber(),
                        "external_transaction_no",
                        ImportRowErrorCode.DUPLICATE_TRANSACTION_IN_FILE,
                        row.localRow().externalTransactionNo(),
                        "Transaction is duplicated within this file"
                ));
            }
        }

        Set<TransactionBusinessKey> existingBusinessKeys =
                loadExistingBusinessKeys(firstRowsByKey.keySet());
        List<ValidatedImportRow> validRows = new ArrayList<>();
        for (Map.Entry<TransactionBusinessKey, ResolvedImportRow> entry
                : firstRowsByKey.entrySet()) {
            ResolvedImportRow row = entry.getValue();
            if (existingBusinessKeys.contains(entry.getKey())) {
                errors.add(error(
                        row.localRow().rowNumber(),
                        "external_transaction_no",
                        ImportRowErrorCode.DUPLICATE_TRANSACTION,
                        row.localRow().externalTransactionNo(),
                        "Transaction already exists"
                ));
                continue;
            }
            validRows.add(row.toValidatedImportRow());
        }

        errors.sort(
                Comparator.comparingInt(
                                ImportRowValidationError::rowNumber
                        )
                        .thenComparingInt(error ->
                                FIELD_ORDER.get(error.fieldName())
                        )
                        .thenComparing(error ->
                                error.errorCode().ordinal()
                        )
        );

        return new ImportFileValidationResult(
                parsedFile.rows().size(),
                validRows,
                errors
        );
    }

    private Map<String, Account> loadAccountsByNumber(
            List<LocallyValidatedImportRow> rows) {
        List<String> accountNos = rows.stream()
                .map(LocallyValidatedImportRow::accountNo)
                .distinct()
                .toList();
        Map<String, Account> accountsByNumber = new HashMap<>();
        for (int start = 0;
                start < accountNos.size();
                start += QUERY_BATCH_SIZE) {
            int end = Math.min(
                    start + QUERY_BATCH_SIZE,
                    accountNos.size()
            );
            accountMapper.selectActiveOrDisabledByAccountNos(
                            accountNos.subList(start, end)
                    )
                    .forEach(account ->
                            accountsByNumber.put(
                                    account.getAccountNo(),
                                    account
                            )
                    );
        }
        return accountsByNumber;
    }

    private Set<TransactionBusinessKey> loadExistingBusinessKeys(
            Set<TransactionBusinessKey> businessKeys) {
        List<TransactionBusinessKey> keyList =
                new ArrayList<>(businessKeys);
        Set<TransactionBusinessKey> existingKeys = new LinkedHashSet<>();
        for (int start = 0;
                start < keyList.size();
                start += QUERY_BATCH_SIZE) {
            int end = Math.min(
                    start + QUERY_BATCH_SIZE,
                    keyList.size()
            );
            existingKeys.addAll(
                    transactionMapper
                            .selectExistingBusinessKeysIncludingDeleted(
                                    TransactionSource.CSV_IMPORT,
                                    keyList.subList(start, end)
                            )
            );
        }
        return existingKeys;
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

    private record ResolvedImportRow(
            LocallyValidatedImportRow localRow,
            Long accountId) {

        private TransactionBusinessKey businessKey() {
            return new TransactionBusinessKey(
                    accountId,
                    localRow.externalTransactionNo()
            );
        }

        private ValidatedImportRow toValidatedImportRow() {
            return new ValidatedImportRow(
                    localRow.rowNumber(),
                    accountId,
                    localRow.externalTransactionNo(),
                    localRow.direction(),
                    localRow.amount(),
                    localRow.transactionTime(),
                    localRow.description(),
                    TransactionSource.CSV_IMPORT
            );
        }
    }
}
