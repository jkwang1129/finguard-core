package com.finguard.core.importjob.validation;

import com.finguard.core.account.entity.Account;
import com.finguard.core.account.mapper.AccountMapper;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.ParsedCsvRow;
import com.finguard.core.importjob.parser.ParsedImportFile;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionBusinessKey;
import com.finguard.core.transaction.model.TransactionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportRowValidatorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-31T02:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private TransactionMapper transactionMapper;

    private CsvImportRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CsvImportRowValidator(
                new CsvImportRowFieldValidator(FIXED_CLOCK),
                accountMapper,
                transactionMapper
        );
    }

    @Test
    void shouldResolveAccountsAndApplyDuplicatePrecedence() {
        when(accountMapper.selectActiveOrDisabledByAccountNos(anyList()))
                .thenReturn(List.of(
                        account(10L, "BANK-A", AccountStatus.ACTIVE),
                        account(11L, "DISABLED", AccountStatus.DISABLED)
                ));
        when(transactionMapper
                .selectExistingBusinessKeysIncludingDeleted(
                        eq(TransactionSource.CSV_IMPORT),
                        anyList()
                ))
                .thenReturn(List.of(
                        new TransactionBusinessKey(10L, "EXT-OLD")
                ));

        ImportFileValidationResult result = validator.validate(file(
                row(2, "BANK-A", "EXT-NEW", "1.00"),
                row(3, "MISSING", "EXT-2", "2.00"),
                row(4, "DISABLED", "EXT-3", "3.00"),
                row(5, "BANK-A", "EXT-4", "0"),
                row(6, "BANK-A", "EXT-NEW", "6.00"),
                row(7, "BANK-A", "EXT-OLD", "7.00"),
                row(8, "BANK-A", "ext-new", "8.00")
        ));

        assertThat(result.validRows())
                .extracting(ValidatedImportRow::rowNumber)
                .containsExactly(2, 8);
        assertThat(result.errors())
                .extracting(ImportRowValidationError::rowNumber)
                .containsExactly(3, 4, 5, 6, 7);
        assertThat(result.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(
                        ImportRowErrorCode.ACCOUNT_NOT_FOUND,
                        ImportRowErrorCode.ACCOUNT_NOT_ACTIVE,
                        ImportRowErrorCode.INVALID_AMOUNT,
                        ImportRowErrorCode.DUPLICATE_TRANSACTION_IN_FILE,
                        ImportRowErrorCode.DUPLICATE_TRANSACTION
                );
        assertThat(result.failedRowCount()).isEqualTo(5);
        assertThat(result.duplicateRowCount()).isEqualTo(2);
    }

    @Test
    void shouldNotLetAnInvalidEarlierRowReserveAFileKey() {
        when(accountMapper.selectActiveOrDisabledByAccountNos(anyList()))
                .thenReturn(List.of(
                        account(10L, "BANK-A", AccountStatus.ACTIVE)
                ));
        when(transactionMapper
                .selectExistingBusinessKeysIncludingDeleted(
                        eq(TransactionSource.CSV_IMPORT),
                        anyList()
                ))
                .thenReturn(List.of());

        ImportFileValidationResult result = validator.validate(file(
                row(2, "BANK-A", "EXT-SAME", "0"),
                row(3, "BANK-A", "EXT-SAME", "3.00")
        ));

        assertThat(result.validRows())
                .extracting(ValidatedImportRow::rowNumber)
                .containsExactly(3);
        assertThat(result.errors())
                .extracting(ImportRowValidationError::errorCode)
                .containsExactly(ImportRowErrorCode.INVALID_AMOUNT);
    }

    @Test
    void shouldSplitMoreThanFiveHundredKeysIntoTwoQueries() {
        List<ParsedCsvRow> rows = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            rows.add(row(
                    index + 2,
                    "ACCOUNT-" + index,
                    "EXT-" + index,
                    "1.00"
            ));
        }
        when(accountMapper.selectActiveOrDisabledByAccountNos(anyList()))
                .thenAnswer(invocation -> {
                    List<String> accountNos = invocation.getArgument(0);
                    return accountNos.stream()
                            .map(accountNo -> account(
                                    Long.parseLong(
                                            accountNo.substring(
                                                    "ACCOUNT-".length()
                                            )
                                    ) + 1,
                                    accountNo,
                                    AccountStatus.ACTIVE
                            ))
                            .toList();
                });
        when(transactionMapper
                .selectExistingBusinessKeysIncludingDeleted(
                        eq(TransactionSource.CSV_IMPORT),
                        anyList()
                ))
                .thenReturn(List.of());

        ImportFileValidationResult result = validator.validate(
                new ParsedImportFile(
                        "batch.csv",
                        "a".repeat(64),
                        1024,
                        rows
                )
        );

        assertThat(result.validRows()).hasSize(501);
        assertThat(result.errors()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> accountBatchCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(accountMapper, times(2))
                .selectActiveOrDisabledByAccountNos(
                        accountBatchCaptor.capture()
                );
        assertThat(accountBatchCaptor.getAllValues())
                .extracting(List::size)
                .containsExactly(500, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionBusinessKey>>
                transactionBatchCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(transactionMapper, times(2))
                .selectExistingBusinessKeysIncludingDeleted(
                        eq(TransactionSource.CSV_IMPORT),
                        transactionBatchCaptor.capture()
                );
        assertThat(transactionBatchCaptor.getAllValues())
                .extracting(List::size)
                .containsExactly(500, 1);
    }

    private ParsedImportFile file(ParsedCsvRow... rows) {
        return new ParsedImportFile(
                "transactions.csv",
                "a".repeat(64),
                1024,
                List.of(rows)
        );
    }

    private ParsedCsvRow row(
            int rowNumber,
            String accountNo,
            String externalTransactionNo,
            String amount) {
        return new ParsedCsvRow(
                rowNumber,
                List.of(
                        accountNo,
                        externalTransactionNo,
                        "INCOME",
                        amount,
                        "2026-07-31 10:00:00",
                        ""
                )
        );
    }

    private Account account(
            Long id,
            String accountNo,
            AccountStatus status) {
        Account account = new Account();
        account.setId(id);
        account.setAccountNo(accountNo);
        account.setStatus(status);
        account.setDeleted(false);
        return account;
    }
}
