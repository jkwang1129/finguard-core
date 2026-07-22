package com.finguard.core.account.service;

import com.finguard.core.account.dto.CreateAccountRequest;
import com.finguard.core.account.dto.UpdateAccountNameRequest;
import com.finguard.core.account.dto.UpdateAccountStatusRequest;
import com.finguard.core.account.entity.Account;
import com.finguard.core.account.exception.AccountNotFoundException;
import com.finguard.core.account.exception.DuplicateAccountNoException;
import com.finguard.core.account.exception.InvalidAccountInputException;
import com.finguard.core.account.exception.InvalidAccountOperationException;
import com.finguard.core.account.mapper.AccountMapper;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.account.model.AccountType;
import com.finguard.core.account.service.impl.AccountServiceImpl;
import com.finguard.core.account.vo.AccountResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountServiceImpl accountService;

    @Test
    void createShouldNormalizeInputAndApplyDefaults() {
        CreateAccountRequest request = new CreateAccountRequest(
                " cash_main ",
                " 日常现金 ",
                AccountType.CASH
        );
        Account storedAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "日常现金",
                AccountStatus.ACTIVE
        );

        when(accountMapper.countByAccountNoIncludingDeleted("CASH_MAIN"))
                .thenReturn(0L);
        when(accountMapper.insert(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(ACCOUNT_ID);
            return 1;
        });
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(storedAccount);

        AccountResponse response = accountService.create(request);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountMapper).insert(accountCaptor.capture());
        Account insertedAccount = accountCaptor.getValue();

        assertThat(insertedAccount.getAccountNo()).isEqualTo("CASH_MAIN");
        assertThat(insertedAccount.getAccountName()).isEqualTo("日常现金");
        assertThat(insertedAccount.getAccountType()).isEqualTo(AccountType.CASH);
        assertThat(insertedAccount.getCurrency()).isEqualTo("CNY");
        assertThat(insertedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(insertedAccount.getDeleted()).isFalse();
        assertThat(response.id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.accountNo()).isEqualTo("CASH_MAIN");
        assertThat(response.createdAt()).isEqualTo(storedAccount.getCreatedAt());
    }

    @Test
    void createShouldRejectInvalidAccountNumberBeforeCallingMapper() {
        CreateAccountRequest request = new CreateAccountRequest(
                "a!",
                "现金账户",
                AccountType.CASH
        );

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(InvalidAccountInputException.class)
                .hasMessageContaining("Account number");

        verifyNoInteractions(accountMapper);
    }

    @Test
    void createShouldRejectBlankAccountNameBeforeCallingMapper() {
        CreateAccountRequest request = new CreateAccountRequest(
                "CASH_MAIN",
                "   ",
                AccountType.CASH
        );

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(InvalidAccountInputException.class)
                .hasMessageContaining("Account name");

        verifyNoInteractions(accountMapper);
    }

    @Test
    void createShouldRejectExistingAccountNumberIncludingDeletedRows() {
        CreateAccountRequest request = new CreateAccountRequest(
                " cash_main ",
                "现金账户",
                AccountType.CASH
        );
        when(accountMapper.countByAccountNoIncludingDeleted("CASH_MAIN"))
                .thenReturn(1L);

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(DuplicateAccountNoException.class)
                .hasMessageContaining("CASH_MAIN");

        verify(accountMapper, never()).insert(any(Account.class));
    }

    @Test
    void createShouldTranslateDatabaseDuplicateRaceToBusinessException() {
        CreateAccountRequest request = new CreateAccountRequest(
                "CASH_MAIN",
                "现金账户",
                AccountType.CASH
        );
        when(accountMapper.countByAccountNoIncludingDeleted("CASH_MAIN"))
                .thenReturn(0L);
        when(accountMapper.insert(any(Account.class)))
                .thenThrow(new DuplicateKeyException("duplicate account number"));

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(DuplicateAccountNoException.class)
                .hasMessageContaining("CASH_MAIN");
    }

    @Test
    void getByIdShouldReturnExistingAccount() {
        Account storedAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.ACTIVE
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(storedAccount);

        AccountResponse response = accountService.getById(ACCOUNT_ID);

        assertThat(response.id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.accountNo()).isEqualTo("CASH_MAIN");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void getByIdShouldRejectMissingAccount() {
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> accountService.getById(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID.toString());
    }

    @Test
    void getByIdShouldRejectNonPositiveIdBeforeCallingMapper() {
        assertThatThrownBy(() -> accountService.getById(0L))
                .isInstanceOf(InvalidAccountInputException.class)
                .hasMessageContaining("positive");

        verifyNoInteractions(accountMapper);
    }

    @Test
    void updateNameShouldNormalizeAndPersistNewName() {
        Account existingAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "旧名称",
                AccountStatus.ACTIVE
        );
        Account updatedAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "新名称",
                AccountStatus.ACTIVE
        );
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(existingAccount, updatedAccount);
        when(accountMapper.updateAccountName(ACCOUNT_ID, "新名称"))
                .thenReturn(1);

        AccountResponse response = accountService.updateName(
                ACCOUNT_ID,
                new UpdateAccountNameRequest(" 新名称 ")
        );

        assertThat(response.accountName()).isEqualTo("新名称");
        verify(accountMapper).updateAccountName(ACCOUNT_ID, "新名称");
    }

    @Test
    void updateNameShouldTreatSameNormalizedNameAsIdempotentSuccess() {
        Account existingAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.ACTIVE
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(existingAccount);

        AccountResponse response = accountService.updateName(
                ACCOUNT_ID,
                new UpdateAccountNameRequest(" 现金账户 ")
        );

        assertThat(response.accountName()).isEqualTo("现金账户");
        verify(accountMapper, never()).updateAccountName(any(), any());
    }

    @Test
    void updateStatusShouldPersistChangedStatus() {
        Account activeAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.ACTIVE
        );
        Account disabledAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.DISABLED
        );
        when(accountMapper.selectById(ACCOUNT_ID))
                .thenReturn(activeAccount, disabledAccount);
        when(accountMapper.updateAccountStatus(ACCOUNT_ID, AccountStatus.DISABLED))
                .thenReturn(1);

        AccountResponse response = accountService.updateStatus(
                ACCOUNT_ID,
                new UpdateAccountStatusRequest(AccountStatus.DISABLED)
        );

        assertThat(response.status()).isEqualTo(AccountStatus.DISABLED);
        verify(accountMapper).updateAccountStatus(
                ACCOUNT_ID,
                AccountStatus.DISABLED
        );
    }

    @Test
    void updateStatusShouldTreatRepeatedAssignmentAsIdempotentSuccess() {
        Account disabledAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.DISABLED
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(disabledAccount);

        AccountResponse response = accountService.updateStatus(
                ACCOUNT_ID,
                new UpdateAccountStatusRequest(AccountStatus.DISABLED)
        );

        assertThat(response.status()).isEqualTo(AccountStatus.DISABLED);
        verify(accountMapper, never()).updateAccountStatus(any(), any());
    }

    @Test
    void deleteShouldRejectActiveAccount() {
        Account activeAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.ACTIVE
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(activeAccount);

        assertThatThrownBy(() -> accountService.delete(ACCOUNT_ID))
                .isInstanceOf(InvalidAccountOperationException.class)
                .hasMessageContaining("disabled");

        verify(accountMapper, never()).countAllTransactionsByAccountId(ACCOUNT_ID);
        verify(accountMapper, never()).deleteById(ACCOUNT_ID);
    }

    @Test
    void deleteShouldRejectAccountWithAnyTransactionHistory() {
        Account disabledAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.DISABLED
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(disabledAccount);
        when(accountMapper.countAllTransactionsByAccountId(ACCOUNT_ID))
                .thenReturn(1L);

        assertThatThrownBy(() -> accountService.delete(ACCOUNT_ID))
                .isInstanceOf(InvalidAccountOperationException.class)
                .hasMessageContaining("transaction history");

        verify(accountMapper, never()).deleteById(ACCOUNT_ID);
    }

    @Test
    void deleteShouldSoftDeleteDisabledAccountWithoutTransactions() {
        Account disabledAccount = account(
                ACCOUNT_ID,
                "CASH_MAIN",
                "现金账户",
                AccountStatus.DISABLED
        );
        when(accountMapper.selectById(ACCOUNT_ID)).thenReturn(disabledAccount);
        when(accountMapper.countAllTransactionsByAccountId(ACCOUNT_ID))
                .thenReturn(0L);
        when(accountMapper.deleteById(ACCOUNT_ID)).thenReturn(1);

        accountService.delete(ACCOUNT_ID);

        verify(accountMapper).deleteById(ACCOUNT_ID);
    }

    private Account account(
            Long id,
            String accountNo,
            String accountName,
            AccountStatus status
    ) {
        Account account = new Account();
        account.setId(id);
        account.setAccountNo(accountNo);
        account.setAccountName(accountName);
        account.setAccountType(AccountType.CASH);
        account.setCurrency("CNY");
        account.setStatus(status);
        account.setCreatedAt(LocalDateTime.of(2026, 7, 22, 10, 0));
        account.setUpdatedAt(LocalDateTime.of(2026, 7, 22, 10, 0));
        account.setDeleted(false);
        return account;
    }
}
