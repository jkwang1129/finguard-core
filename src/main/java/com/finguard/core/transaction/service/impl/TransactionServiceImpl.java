package com.finguard.core.transaction.service.impl;

import com.finguard.core.account.entity.Account;
import com.finguard.core.account.exception.AccountNotFoundException;
import com.finguard.core.account.mapper.AccountMapper;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.exception.DuplicateTransactionException;
import com.finguard.core.transaction.exception.InvalidTransactionInputException;
import com.finguard.core.transaction.exception.InvalidTransactionOperationException;
import com.finguard.core.transaction.exception.TransactionNotFoundException;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.finguard.core.transaction.service.TransactionService;
import com.finguard.core.transaction.vo.TransactionResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final int MAX_EXTERNAL_TRANSACTION_NO_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MAX_AMOUNT_INTEGER_DIGITS = 17;
    private static final int MAX_AMOUNT_DECIMAL_PLACES = 2;
    private static final int MAX_FUTURE_MINUTES = 5;
    private static final TransactionSource MANUAL_SOURCE = TransactionSource.MANUAL;

    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final Clock businessClock;

    public TransactionServiceImpl(
            TransactionMapper transactionMapper,
            AccountMapper accountMapper,
            Clock businessClock) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
        this.businessClock = businessClock;
    }

    @Override
    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        if (request == null) {
            throw new InvalidTransactionInputException(
                    "Create transaction request must not be null"
            );
        }

        Long accountId = requirePositiveId(request.accountId(), "Account id");
        String externalTransactionNo = normalizeExternalTransactionNo(
                request.externalTransactionNo()
        );
        TransactionDirection direction = requireDirection(request.direction());
        BigDecimal amount = normalizeAmount(request.amount());
        LocalDateTime transactionTime = normalizeTransactionTime(
                request.transactionTime()
        );
        String description = normalizeDescription(request.description());

        Account account = requireAccount(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionOperationException(
                    "Only an active account can receive a new transaction"
            );
        }

        if (transactionMapper.countByBusinessKeyIncludingDeleted(
                accountId,
                MANUAL_SOURCE,
                externalTransactionNo
        ) > 0) {
            throw new DuplicateTransactionException(
                    accountId,
                    MANUAL_SOURCE,
                    externalTransactionNo
            );
        }

        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        transaction.setExternalTransactionNo(externalTransactionNo);
        transaction.setDirection(direction);
        transaction.setAmount(amount);
        transaction.setTransactionTime(transactionTime);
        transaction.setDescription(description);
        transaction.setSource(MANUAL_SOURCE);
        transaction.setDeleted(false);

        try {
            int insertedRows = transactionMapper.insert(transaction);
            if (insertedRows != 1 || transaction.getId() == null) {
                throw new InvalidTransactionOperationException(
                        "Transaction could not be created"
                );
            }
        } catch (DuplicateKeyException exception) {
            throw new DuplicateTransactionException(
                    accountId,
                    MANUAL_SOURCE,
                    externalTransactionNo
            );
        }

        return toResponse(requireTransaction(transaction.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getById(Long transactionId) {
        return toResponse(requireTransaction(transactionId));
    }

    @Override
    @Transactional
    public TransactionResponse update(
            Long transactionId,
            UpdateTransactionRequest request) {
        Long validatedTransactionId = requirePositiveId(
                transactionId,
                "Transaction id"
        );
        if (request == null) {
            throw new InvalidTransactionInputException(
                    "Update transaction request must not be null"
            );
        }

        TransactionDirection direction = requireDirection(request.direction());
        BigDecimal amount = normalizeAmount(request.amount());
        LocalDateTime transactionTime = normalizeTransactionTime(
                request.transactionTime()
        );
        String description = normalizeDescription(request.description());

        Transaction transaction = requireTransaction(validatedTransactionId);
        requireManualTransaction(transaction);

        if (transaction.getDirection() == direction
                && transaction.getAmount().compareTo(amount) == 0
                && transaction.getTransactionTime().equals(transactionTime)
                && Objects.equals(transaction.getDescription(), description)) {
            return toResponse(transaction);
        }

        int updatedRows = transactionMapper.updateMutableFields(
                validatedTransactionId,
                direction,
                amount,
                transactionTime,
                description
        );
        if (updatedRows != 1) {
            throw new TransactionNotFoundException(validatedTransactionId);
        }

        return toResponse(requireTransaction(validatedTransactionId));
    }

    @Override
    @Transactional
    public void delete(Long transactionId) {
        Long validatedTransactionId = requirePositiveId(
                transactionId,
                "Transaction id"
        );
        Transaction transaction = transactionMapper.selectByIdIncludingDeleted(
                validatedTransactionId
        );
        if (transaction == null) {
            throw new TransactionNotFoundException(validatedTransactionId);
        }

        requireManualTransaction(transaction);

        if (Boolean.TRUE.equals(transaction.getDeleted())) {
            return;
        }

        Account account = requireAccount(transaction.getAccountId());
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionOperationException(
                    "A transaction can be deleted only while its account is active"
            );
        }

        int deletedRows = transactionMapper.softDeleteById(
                validatedTransactionId
        );
        if (deletedRows == 1) {
            return;
        }

        Transaction latestTransaction =
                transactionMapper.selectByIdIncludingDeleted(
                        validatedTransactionId
                );
        if (latestTransaction != null
                && Boolean.TRUE.equals(latestTransaction.getDeleted())) {
            return;
        }
        throw new TransactionNotFoundException(validatedTransactionId);
    }

    private Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new InvalidTransactionInputException(
                    fieldName + " must be positive"
            );
        }
        return id;
    }

    private String normalizeExternalTransactionNo(
            String rawExternalTransactionNo) {
        if (rawExternalTransactionNo == null) {
            throw new InvalidTransactionInputException(
                    "External transaction number must not be null"
            );
        }

        String externalTransactionNo = rawExternalTransactionNo.trim();
        if (externalTransactionNo.isEmpty()
                || externalTransactionNo.length()
                > MAX_EXTERNAL_TRANSACTION_NO_LENGTH) {
            throw new InvalidTransactionInputException(
                    "External transaction number must be between 1 and "
                            + MAX_EXTERNAL_TRANSACTION_NO_LENGTH
                            + " characters"
            );
        }
        return externalTransactionNo;
    }

    private TransactionDirection requireDirection(
            TransactionDirection direction) {
        if (direction == null) {
            throw new InvalidTransactionInputException(
                    "Transaction direction must not be null"
            );
        }
        return direction;
    }

    private BigDecimal normalizeAmount(BigDecimal rawAmount) {
        if (rawAmount == null) {
            throw new InvalidTransactionInputException(
                    "Transaction amount must not be null"
            );
        }
        if (rawAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionInputException(
                    "Transaction amount must be greater than zero"
            );
        }
        if (rawAmount.scale() > MAX_AMOUNT_DECIMAL_PLACES) {
            throw new InvalidTransactionInputException(
                    "Transaction amount must have at most two decimal places"
            );
        }

        long integerDigits = (long) rawAmount.precision() - rawAmount.scale();
        if (integerDigits > MAX_AMOUNT_INTEGER_DIGITS) {
            throw new InvalidTransactionInputException(
                    "Transaction amount must have at most 17 integer digits"
            );
        }

        return rawAmount.setScale(
                MAX_AMOUNT_DECIMAL_PLACES,
                RoundingMode.UNNECESSARY
        );
    }

    private LocalDateTime normalizeTransactionTime(
            LocalDateTime rawTransactionTime) {
        if (rawTransactionTime == null) {
            throw new InvalidTransactionInputException(
                    "Transaction time must not be null"
            );
        }

        LocalDateTime latestAllowedTime =
                LocalDateTime.now(businessClock).plusMinutes(
                        MAX_FUTURE_MINUTES
                );
        if (rawTransactionTime.isAfter(latestAllowedTime)) {
            throw new InvalidTransactionInputException(
                    "Transaction time must not be more than five minutes "
                            + "in the future"
            );
        }

        int milliseconds = rawTransactionTime.getNano() / 1_000_000;
        return rawTransactionTime.withNano(milliseconds * 1_000_000);
    }

    private String normalizeDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }

        String description = rawDescription.trim();
        if (description.isEmpty()) {
            return null;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTransactionInputException(
                    "Transaction description must not exceed "
                            + MAX_DESCRIPTION_LENGTH
                            + " characters"
            );
        }
        return description;
    }

    private Account requireAccount(Long accountId) {
        Long validatedAccountId = requirePositiveId(accountId, "Account id");
        Account account = accountMapper.selectById(validatedAccountId);
        if (account == null) {
            throw new AccountNotFoundException(validatedAccountId);
        }
        return account;
    }

    private Transaction requireTransaction(Long transactionId) {
        Long validatedTransactionId = requirePositiveId(
                transactionId,
                "Transaction id"
        );
        Transaction transaction = transactionMapper.selectById(
                validatedTransactionId
        );
        if (transaction == null) {
            throw new TransactionNotFoundException(validatedTransactionId);
        }
        return transaction;
    }

    private void requireManualTransaction(Transaction transaction) {
        if (transaction.getSource() != MANUAL_SOURCE) {
            throw new InvalidTransactionOperationException(
                    "Only a MANUAL transaction can be changed or deleted"
            );
        }
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getExternalTransactionNo(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getTransactionTime(),
                transaction.getDescription(),
                transaction.getSource(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
