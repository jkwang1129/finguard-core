package com.finguard.core.transaction.service;

import com.finguard.core.account.entity.Account;
import com.finguard.core.account.exception.AccountNotFoundException;
import com.finguard.core.account.mapper.AccountMapper;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.TransactionQueryRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.exception.DuplicateTransactionException;
import com.finguard.core.transaction.exception.InvalidTransactionInputException;
import com.finguard.core.transaction.exception.InvalidTransactionOperationException;
import com.finguard.core.transaction.exception.TransactionNotFoundException;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.finguard.core.transaction.service.impl.TransactionServiceImpl;
import com.finguard.core.transaction.vo.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long TRANSACTION_ID = 10L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 23, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-23T02:00:00Z"),
            BUSINESS_ZONE
    );

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private AccountMapper accountMapper;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(
                transactionMapper,
                accountMapper,
                FIXED_CLOCK
        );
    }

    @Test
    void createShouldNormalizeInputAndPersistManualTransaction() {
        LocalDateTime rawTime = NOW.minusHours(1).withNano(123_456_789);
        LocalDateTime storedTime = rawTime.withNano(123_000_000);
        CreateTransactionRequest request = new CreateTransactionRequest(
                ACCOUNT_ID,
                "  Tx-001  ",
                TransactionDirection.EXPENSE,
                new BigDecimal("128.5"),
                rawTime,
                "  午餐  "
        );
        Transaction storedTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("128.50"),
                storedTime,
                "午餐"
        );

        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.ACTIVE));
        when(transactionMapper.countByBusinessKeyIncludingDeleted(
                ACCOUNT_ID,
                TransactionSource.MANUAL,
                "Tx-001"
        )).thenReturn(0L);
        when(transactionMapper.insert(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setId(TRANSACTION_ID);
                    return 1;
                });
        when(transactionMapper.selectById(TRANSACTION_ID))
                .thenReturn(storedTransaction);

        TransactionResponse response = transactionService.create(request);

        ArgumentCaptor<Transaction> captor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(captor.capture());
        Transaction insertedTransaction = captor.getValue();

        assertThat(insertedTransaction.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(insertedTransaction.getExternalTransactionNo())
                .isEqualTo("Tx-001");
        assertThat(insertedTransaction.getDirection())
                .isEqualTo(TransactionDirection.EXPENSE);
        assertThat(insertedTransaction.getAmount())
                .isEqualByComparingTo("128.50");
        assertThat(insertedTransaction.getTransactionTime())
                .isEqualTo(storedTime);
        assertThat(insertedTransaction.getDescription()).isEqualTo("午餐");
        assertThat(insertedTransaction.getSource())
                .isEqualTo(TransactionSource.MANUAL);
        assertThat(insertedTransaction.getDeleted()).isFalse();
        assertThat(response.id()).isEqualTo(TRANSACTION_ID);
        assertThat(response.source()).isEqualTo(TransactionSource.MANUAL);
    }

    @Test
    void queryShouldRejectReversedTimeRangeBeforeCallingMapper() {
        TransactionQueryRequest request = new TransactionQueryRequest(
                1L,
                20L,
                ACCOUNT_ID,
                null,
                null,
                null,
                NOW,
                NOW.minusDays(1)
        );

        assertThatThrownBy(() -> transactionService.query(request))
                .isInstanceOf(InvalidTransactionInputException.class)
                .hasMessage("Start time must not be later than end time");

        verifyNoInteractions(transactionMapper, accountMapper);
    }

    @Test
    void createShouldRejectNullRequestBeforeCallingMappers() {
        assertThatThrownBy(() -> transactionService.create(null))
                .isInstanceOf(InvalidTransactionInputException.class)
                .hasMessageContaining("must not be null");

        verifyNoInteractions(accountMapper, transactionMapper);
    }

    @Test
    void createShouldRejectBlankExternalNumberBeforeCallingMappers() {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal("10.00"),
                NOW,
                "   "
        );

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(InvalidTransactionInputException.class)
                .hasMessageContaining("External transaction number");

        verifyNoInteractions(accountMapper, transactionMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1.00",
            "12.345",
            "100000000000000000.00"
    })
    void createShouldRejectInvalidAmountBeforeCallingMappers(String amount) {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal(amount),
                NOW,
                "TX-001"
        );

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(InvalidTransactionInputException.class)
                .hasMessageContaining("amount");

        verifyNoInteractions(accountMapper, transactionMapper);
    }

    @Test
    void createShouldRejectTimeMoreThanFiveMinutesInFuture() {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal("10.00"),
                NOW.plusMinutes(5).plusNanos(1),
                "TX-001"
        );

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(InvalidTransactionInputException.class)
                .hasMessageContaining("five minutes");

        verifyNoInteractions(accountMapper, transactionMapper);
    }

    @Test
    void createShouldRejectMissingAccountBeforeCallingTransactionMapper() {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal("10.00"),
                NOW,
                "TX-001"
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID.toString());

        verifyNoInteractions(transactionMapper);
    }

    @Test
    void createShouldRejectDisabledAccountBeforeCallingTransactionMapper() {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal("10.00"),
                NOW,
                "TX-001"
        );
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.DISABLED));

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(InvalidTransactionOperationException.class)
                .hasMessageContaining("active account");

        verifyNoInteractions(transactionMapper);
    }

    @Test
    void createShouldRejectExistingBusinessKeyIncludingDeletedRows() {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal("10.00"),
                NOW,
                "TX-001"
        );
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.ACTIVE));
        when(transactionMapper.countByBusinessKeyIncludingDeleted(
                ACCOUNT_ID,
                TransactionSource.MANUAL,
                "TX-001"
        )).thenReturn(1L);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("TX-001");

        verify(transactionMapper, never()).insert(any(Transaction.class));
    }

    @Test
    void createShouldTranslateDatabaseDuplicateRaceToBusinessException() {
        CreateTransactionRequest request = validCreateRequest(
                new BigDecimal("10.00"),
                NOW,
                "TX-001"
        );
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.ACTIVE));
        when(transactionMapper.countByBusinessKeyIncludingDeleted(
                ACCOUNT_ID,
                TransactionSource.MANUAL,
                "TX-001"
        )).thenReturn(0L);
        when(transactionMapper.insert(any(Transaction.class)))
                .thenThrow(new DuplicateKeyException("duplicate transaction"));

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("TX-001");
    }

    @Test
    void getByIdShouldReturnExistingTransaction() {
        Transaction storedTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.INCOME,
                new BigDecimal("50.00"),
                NOW,
                "退款"
        );
        when(transactionMapper.selectById(TRANSACTION_ID))
                .thenReturn(storedTransaction);

        TransactionResponse response =
                transactionService.getById(TRANSACTION_ID);

        assertThat(response.id()).isEqualTo(TRANSACTION_ID);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.direction())
                .isEqualTo(TransactionDirection.INCOME);
    }

    @Test
    void getByIdShouldRejectMissingTransaction() {
        when(transactionMapper.selectById(TRANSACTION_ID)).thenReturn(null);

        assertThatThrownBy(
                () -> transactionService.getById(TRANSACTION_ID)
        ).isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(TRANSACTION_ID.toString());
    }

    @Test
    void getByIdShouldRejectNonPositiveIdBeforeCallingMappers() {
        assertThatThrownBy(() -> transactionService.getById(0L))
                .isInstanceOf(InvalidTransactionInputException.class)
                .hasMessageContaining("positive");

        verifyNoInteractions(accountMapper, transactionMapper);
    }

    @Test
    void updateShouldNormalizeAndPersistMutableFields() {
        LocalDateTime rawNewTime =
                NOW.minusMinutes(30).withNano(987_654_321);
        LocalDateTime storedNewTime =
                rawNewTime.withNano(987_000_000);
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.INCOME,
                new BigDecimal("20.00"),
                NOW.minusHours(1),
                "旧描述"
        );
        Transaction updatedTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("55.00"),
                storedNewTime,
                "新描述"
        );
        when(transactionMapper.selectById(TRANSACTION_ID))
                .thenReturn(existingTransaction, updatedTransaction);
        when(transactionMapper.updateMutableFields(
                TRANSACTION_ID,
                TransactionDirection.EXPENSE,
                new BigDecimal("55.00"),
                storedNewTime,
                "新描述"
        )).thenReturn(1);

        TransactionResponse response = transactionService.update(
                TRANSACTION_ID,
                new UpdateTransactionRequest(
                        TransactionDirection.EXPENSE,
                        new BigDecimal("55"),
                        rawNewTime,
                        "  新描述  "
                )
        );

        verify(transactionMapper).updateMutableFields(
                TRANSACTION_ID,
                TransactionDirection.EXPENSE,
                new BigDecimal("55.00"),
                storedNewTime,
                "新描述"
        );
        assertThat(response.amount()).isEqualByComparingTo("55.00");
        assertThat(response.description()).isEqualTo("新描述");
    }

    @Test
    void updateShouldTreatSameNormalizedValuesAsIdempotentSuccess() {
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("55.00"),
                NOW,
                "午餐"
        );
        when(transactionMapper.selectById(TRANSACTION_ID))
                .thenReturn(existingTransaction);

        TransactionResponse response = transactionService.update(
                TRANSACTION_ID,
                new UpdateTransactionRequest(
                        TransactionDirection.EXPENSE,
                        new BigDecimal("55.0"),
                        NOW,
                        "  午餐  "
                )
        );

        assertThat(response.id()).isEqualTo(TRANSACTION_ID);
        verify(transactionMapper, never()).updateMutableFields(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void updateShouldRejectCsvImportTransaction() {
        Transaction csvTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.CSV_IMPORT,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectById(TRANSACTION_ID))
                .thenReturn(csvTransaction);

        assertThatThrownBy(() -> transactionService.update(
                TRANSACTION_ID,
                validUpdateRequest()
        )).isInstanceOf(InvalidTransactionOperationException.class)
                .hasMessageContaining("MANUAL");

        verify(transactionMapper, never()).updateMutableFields(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void updateShouldRejectFutureTimeBeforeCallingMappers() {
        UpdateTransactionRequest request = new UpdateTransactionRequest(
                TransactionDirection.INCOME,
                new BigDecimal("20.00"),
                NOW.plusMinutes(6),
                null
        );

        assertThatThrownBy(() -> transactionService.update(
                TRANSACTION_ID,
                request
        )).isInstanceOf(InvalidTransactionInputException.class)
                .hasMessageContaining("five minutes");

        verifyNoInteractions(accountMapper, transactionMapper);
    }

    @Test
    void updateShouldRejectTransactionDeletedDuringUpdate() {
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.INCOME,
                new BigDecimal("20.00"),
                NOW.minusHours(1),
                null
        );
        when(transactionMapper.selectById(TRANSACTION_ID))
                .thenReturn(existingTransaction);
        when(transactionMapper.updateMutableFields(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0);

        assertThatThrownBy(() -> transactionService.update(
                TRANSACTION_ID,
                validUpdateRequest()
        )).isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(TRANSACTION_ID.toString());
    }

    @Test
    void deleteShouldSoftDeleteManualTransactionOnActiveAccount() {
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(existingTransaction);
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.ACTIVE));
        when(transactionMapper.softDeleteById(TRANSACTION_ID)).thenReturn(1);

        assertThatCode(() -> transactionService.delete(TRANSACTION_ID))
                .doesNotThrowAnyException();

        verify(transactionMapper).softDeleteById(TRANSACTION_ID);
    }

    @Test
    void deleteShouldTreatAlreadyDeletedTransactionAsIdempotentSuccess() {
        Transaction deletedTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                true,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(deletedTransaction);

        assertThatCode(() -> transactionService.delete(TRANSACTION_ID))
                .doesNotThrowAnyException();

        verifyNoInteractions(accountMapper);
        verify(transactionMapper, never()).softDeleteById(TRANSACTION_ID);
    }

    @Test
    void deleteShouldRejectAlreadyDeletedCsvImportTransaction() {
        Transaction deletedCsvTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.CSV_IMPORT,
                true,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(deletedCsvTransaction);

        assertThatThrownBy(() -> transactionService.delete(TRANSACTION_ID))
                .isInstanceOf(InvalidTransactionOperationException.class)
                .hasMessageContaining("MANUAL");

        verifyNoInteractions(accountMapper);
        verify(transactionMapper, never()).softDeleteById(TRANSACTION_ID);
    }

    @Test
    void deleteShouldRejectMissingTransaction() {
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> transactionService.delete(TRANSACTION_ID))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(TRANSACTION_ID.toString());

        verifyNoInteractions(accountMapper);
    }

    @Test
    void deleteShouldRejectCsvImportTransaction() {
        Transaction csvTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.CSV_IMPORT,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(csvTransaction);

        assertThatThrownBy(() -> transactionService.delete(TRANSACTION_ID))
                .isInstanceOf(InvalidTransactionOperationException.class)
                .hasMessageContaining("MANUAL");

        verifyNoInteractions(accountMapper);
        verify(transactionMapper, never()).softDeleteById(TRANSACTION_ID);
    }

    @Test
    void deleteShouldRejectTransactionOnDisabledAccount() {
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(existingTransaction);
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.DISABLED));

        assertThatThrownBy(() -> transactionService.delete(TRANSACTION_ID))
                .isInstanceOf(InvalidTransactionOperationException.class)
                .hasMessageContaining("account is active");

        verify(transactionMapper, never()).softDeleteById(TRANSACTION_ID);
    }

    @Test
    void deleteShouldTreatConcurrentDeleteAsIdempotentSuccess() {
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        Transaction concurrentlyDeletedTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                true,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(
                        existingTransaction,
                        concurrentlyDeletedTransaction
                );
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.ACTIVE));
        when(transactionMapper.softDeleteById(TRANSACTION_ID)).thenReturn(0);

        assertThatCode(() -> transactionService.delete(TRANSACTION_ID))
                .doesNotThrowAnyException();

        verify(transactionMapper).softDeleteById(TRANSACTION_ID);
    }

    @Test
    void deleteShouldRejectMissingRowAfterFailedSoftDelete() {
        Transaction existingTransaction = transaction(
                TRANSACTION_ID,
                TransactionSource.MANUAL,
                false,
                TransactionDirection.EXPENSE,
                new BigDecimal("20.00"),
                NOW,
                null
        );
        when(transactionMapper.selectByIdIncludingDeleted(TRANSACTION_ID))
                .thenReturn(existingTransaction, (Transaction) null);
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(account(AccountStatus.ACTIVE));
        when(transactionMapper.softDeleteById(TRANSACTION_ID)).thenReturn(0);

        assertThatThrownBy(() -> transactionService.delete(TRANSACTION_ID))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(TRANSACTION_ID.toString());
    }

    private CreateTransactionRequest validCreateRequest(
            BigDecimal amount,
            LocalDateTime transactionTime,
            String externalTransactionNo) {
        return new CreateTransactionRequest(
                ACCOUNT_ID,
                externalTransactionNo,
                TransactionDirection.EXPENSE,
                amount,
                transactionTime,
                "测试交易"
        );
    }

    private UpdateTransactionRequest validUpdateRequest() {
        return new UpdateTransactionRequest(
                TransactionDirection.EXPENSE,
                new BigDecimal("30.00"),
                NOW.minusMinutes(30),
                "更新后的交易"
        );
    }

    private Account account(AccountStatus status) {
        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setStatus(status);
        account.setDeleted(false);
        return account;
    }

    private Transaction transaction(
            Long id,
            TransactionSource source,
            boolean deleted,
            TransactionDirection direction,
            BigDecimal amount,
            LocalDateTime transactionTime,
            String description) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setAccountId(ACCOUNT_ID);
        transaction.setExternalTransactionNo("TX-001");
        transaction.setDirection(direction);
        transaction.setAmount(amount);
        transaction.setTransactionTime(transactionTime);
        transaction.setDescription(description);
        transaction.setSource(source);
        transaction.setCreatedAt(NOW.minusDays(1));
        transaction.setUpdatedAt(NOW.minusDays(1));
        transaction.setDeleted(deleted);
        return transaction;
    }
}
