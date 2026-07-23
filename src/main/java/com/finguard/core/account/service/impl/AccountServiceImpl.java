package com.finguard.core.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.account.dto.AccountQueryRequest;
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
import com.finguard.core.account.service.AccountService;
import com.finguard.core.account.vo.AccountResponse;
import com.finguard.core.common.vo.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AccountServiceImpl implements AccountService {

    static final Pattern ACCOUNT_NO_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9_-]{2,63}$");
    static final String DEFAULT_CURRENCY = "CNY";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_QUERY_KEYWORD_LENGTH = 100;

    private final AccountMapper accountMapper;

    public AccountServiceImpl(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (request == null) {
            throw new InvalidAccountInputException("Create account request must not be null");
        }

        String accountNo = normalizeAccountNo(request.accountNo());
        String accountName = normalizeAccountName(request.accountName());
        if (request.accountType() == null) {
            throw new InvalidAccountInputException("Account type must not be null");
        }

        if (accountMapper.countByAccountNoIncludingDeleted(accountNo) > 0) {
            throw new DuplicateAccountNoException(accountNo);
        }

        Account account = new Account();
        account.setAccountNo(accountNo);
        account.setAccountName(accountName);
        account.setAccountType(request.accountType());
        account.setCurrency(DEFAULT_CURRENCY);
        account.setStatus(AccountStatus.ACTIVE);
        account.setDeleted(false);

        try {
            int insertedRows = accountMapper.insert(account);
            if (insertedRows != 1 || account.getId() == null) {
                throw new InvalidAccountOperationException("Account could not be created");
            }
        } catch (DuplicateKeyException exception) {
            throw new DuplicateAccountNoException(accountNo);
        }

        return toResponse(requireAccount(account.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getById(Long accountId) {
        return toResponse(requireAccount(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> query(AccountQueryRequest request) {
        validateQueryRequest(request);
        String keyword = normalizeOptionalQueryKeyword(request.keyword());

        Page<Account> page = new Page<>(request.page(), request.size());
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(
                request.status() != null,
                Account::getStatus,
                request.status()
        );
        wrapper.eq(
                request.accountType() != null,
                Account::getAccountType,
                request.accountType()
        );
        wrapper.and(
                keyword != null,
                nested -> nested
                        .like(Account::getAccountNo, keyword)
                        .or()
                        .like(Account::getAccountName, keyword)
        );
        wrapper.orderByDesc(Account::getId);

        Page<Account> result = accountMapper.selectPage(page, wrapper);
        List<AccountResponse> records = result.getRecords()
                .stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getPages(),
                records
        );
    }

    @Override
    @Transactional
    public AccountResponse updateName(Long accountId, UpdateAccountNameRequest request) {
        if (request == null) {
            throw new InvalidAccountInputException("Update account name request must not be null");
        }

        Account account = requireAccount(accountId);
        String accountName = normalizeAccountName(request.accountName());
        if (account.getAccountName().equals(accountName)) {
            return toResponse(account);
        }

        int updatedRows = accountMapper.updateAccountName(accountId, accountName);
        if (updatedRows != 1) {
            throw new AccountNotFoundException(accountId);
        }

        return toResponse(requireAccount(accountId));
    }

    @Override
    @Transactional
    public AccountResponse updateStatus(Long accountId, UpdateAccountStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new InvalidAccountInputException("Account status must not be null");
        }

        Account account = requireAccount(accountId);
        if (account.getStatus() == request.status()) {
            return toResponse(account);
        }

        int updatedRows = accountMapper.updateAccountStatus(
                accountId,
                request.status()
        );
        if (updatedRows != 1) {
            throw new AccountNotFoundException(accountId);
        }

        return toResponse(requireAccount(accountId));
    }

    @Override
    @Transactional
    public void delete(Long accountId) {
        Account account = requireAccount(accountId);
        if (account.getStatus() != AccountStatus.DISABLED) {
            throw new InvalidAccountOperationException(
                    "Only a disabled account can be deleted"
            );
        }

        long transactionCount = accountMapper.countAllTransactionsByAccountId(accountId);
        if (transactionCount > 0) {
            throw new InvalidAccountOperationException(
                    "An account with transaction history cannot be deleted"
            );
        }

        int deletedRows = accountMapper.deleteById(accountId);
        if (deletedRows != 1) {
            throw new AccountNotFoundException(accountId);
        }
    }

    private String normalizeAccountNo(String rawAccountNo) {
        if (rawAccountNo == null) {
            throw new InvalidAccountInputException("Account number must not be null");
        }

        String accountNo = rawAccountNo.trim().toUpperCase(Locale.ROOT);
        if (!ACCOUNT_NO_PATTERN.matcher(accountNo).matches()) {
            throw new InvalidAccountInputException(
                    "Account number must be 3 to 64 characters and contain only "
                            + "uppercase letters, digits, hyphens or underscores"
            );
        }
        return accountNo;
    }

    private String normalizeAccountName(String rawAccountName) {
        if (rawAccountName == null) {
            throw new InvalidAccountInputException("Account name must not be null");
        }

        String accountName = rawAccountName.trim();
        if (accountName.isEmpty() || accountName.length() > 100) {
            throw new InvalidAccountInputException(
                    "Account name must be between 1 and 100 characters"
            );
        }
        return accountName;
    }

    private void validateQueryRequest(AccountQueryRequest request) {
        if (request == null) {
            throw new InvalidAccountInputException(
                    "Account query request must not be null"
            );
        }
        if (request.page() == null || request.page() < 1) {
            throw new InvalidAccountInputException(
                    "Page must be at least 1"
            );
        }
        if (request.size() == null
                || request.size() < 1
                || request.size() > MAX_PAGE_SIZE) {
            throw new InvalidAccountInputException(
                    "Size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private String normalizeOptionalQueryKeyword(String rawKeyword) {
        if (rawKeyword == null) {
            return null;
        }

        String keyword = rawKeyword.trim();
        if (keyword.isEmpty()) {
            return null;
        }
        if (keyword.length() > MAX_QUERY_KEYWORD_LENGTH) {
            throw new InvalidAccountInputException(
                    "Keyword must not exceed "
                            + MAX_QUERY_KEYWORD_LENGTH
                            + " characters"
            );
        }
        return keyword;
    }

    private Account requireAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidAccountInputException("Account id must be positive");
        }

        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNo(),
                account.getAccountName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
