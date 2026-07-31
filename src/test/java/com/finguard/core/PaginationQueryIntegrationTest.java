package com.finguard.core;

import com.finguard.core.account.dto.AccountQueryRequest;
import com.finguard.core.account.entity.Account;
import com.finguard.core.account.mapper.AccountMapper;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.account.model.AccountType;
import com.finguard.core.account.service.AccountService;
import com.finguard.core.account.vo.AccountResponse;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.transaction.dto.TransactionQueryRequest;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.finguard.core.transaction.service.TransactionService;
import com.finguard.core.transaction.vo.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PaginationQueryIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void accountQueryShouldPaginateFilterAndHideSoftDeletedRows() {
        String token = "QUERY" + System.nanoTime();
        Account first = insertAccount(
                token + "_A",
                token + " Alpha",
                AccountType.CASH,
                AccountStatus.ACTIVE
        );
        Account second = insertAccount(
                token + "_B",
                token + " Beta",
                AccountType.BANK,
                AccountStatus.DISABLED
        );
        Account deleted = insertAccount(
                token + "_C",
                token + " Deleted",
                AccountType.CASH,
                AccountStatus.DISABLED
        );
        assertThat(accountMapper.deleteById(deleted.getId())).isEqualTo(1);

        PageResponse<AccountResponse> firstPage = accountService.query(
                new AccountQueryRequest(1L, 1L, null, null, token)
        );

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.pages()).isEqualTo(2);
        assertThat(firstPage.records()).hasSize(1);
        assertThat(firstPage.records().get(0).id())
                .isEqualTo(second.getId());

        PageResponse<AccountResponse> activeAccounts =
                accountService.query(new AccountQueryRequest(
                        1L,
                        20L,
                        AccountStatus.ACTIVE,
                        AccountType.CASH,
                        token
                ));

        assertThat(activeAccounts.total()).isEqualTo(1);
        assertThat(activeAccounts.records())
                .extracting(AccountResponse::id)
                .containsExactly(first.getId());
    }

    @Test
    void transactionQueryShouldCombineFiltersAndUseStableOrdering() {
        String token = "QUERY" + System.nanoTime();
        Account account = insertAccount(
                token + "_TX",
                token + " Transactions",
                AccountType.BANK,
                AccountStatus.ACTIVE
        );
        LocalDateTime baseTime =
                LocalDateTime.of(2026, 7, 23, 8, 0);

        Transaction first = insertTransaction(
                account.getId(),
                token + "-1",
                TransactionDirection.EXPENSE,
                TransactionSource.MANUAL,
                baseTime
        );
        Transaction second = insertTransaction(
                account.getId(),
                token + "-2",
                TransactionDirection.INCOME,
                TransactionSource.MANUAL,
                baseTime.plusHours(1)
        );
        Transaction third = insertTransaction(
                account.getId(),
                token + "-3",
                TransactionDirection.EXPENSE,
                TransactionSource.CSV_IMPORT,
                baseTime.plusHours(2)
        );
        Transaction deleted = insertTransaction(
                account.getId(),
                token + "-4",
                TransactionDirection.EXPENSE,
                TransactionSource.MANUAL,
                baseTime.plusHours(3)
        );
        assertThat(transactionMapper.softDeleteById(deleted.getId()))
                .isEqualTo(1);

        PageResponse<TransactionResponse> orderedPage =
                transactionService.query(new TransactionQueryRequest(
                        1L,
                        2L,
                        account.getId(),
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        assertThat(orderedPage.total()).isEqualTo(3);
        assertThat(orderedPage.pages()).isEqualTo(2);
        assertThat(orderedPage.records())
                .extracting(TransactionResponse::id)
                .containsExactly(third.getId(), second.getId());

        PageResponse<TransactionResponse> filtered =
                transactionService.query(new TransactionQueryRequest(
                        1L,
                        20L,
                        account.getId(),
                        TransactionDirection.EXPENSE,
                        TransactionSource.MANUAL,
                        "  " + token + "-1  ",
                        baseTime.minusMinutes(1),
                        baseTime.plusMinutes(1)
                ));

        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.records())
                .extracting(TransactionResponse::id)
                .containsExactly(first.getId());
    }

    private Account insertAccount(
            String accountNo,
            String accountName,
            AccountType accountType,
            AccountStatus status) {
        Account account = new Account();
        account.setAccountNo(accountNo);
        account.setAccountName(accountName);
        account.setAccountType(accountType);
        account.setCurrency("CNY");
        account.setStatus(status);
        account.setDeleted(false);
        assertThat(accountMapper.insert(account)).isEqualTo(1);
        assertThat(account.getId()).isNotNull();
        return account;
    }

    private Transaction insertTransaction(
            Long accountId,
            String externalTransactionNo,
            TransactionDirection direction,
            TransactionSource source,
            LocalDateTime transactionTime) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        if (source == TransactionSource.CSV_IMPORT) {
            transaction.setImportJobId(insertImportJob());
        }
        transaction.setExternalTransactionNo(externalTransactionNo);
        transaction.setDirection(direction);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setTransactionTime(transactionTime);
        transaction.setDescription("pagination integration test");
        transaction.setSource(source);
        transaction.setDeleted(false);
        assertThat(transactionMapper.insert(transaction)).isEqualTo(1);
        assertThat(transaction.getId()).isNotNull();
        return transaction;
    }

    private Long insertImportJob() {
        String token = UUID.randomUUID().toString();
        String username = "pagination-" + token;
        jdbcTemplate.update(
                """
                INSERT INTO users (username, password_hash, status)
                VALUES (?, ?, 'ACTIVE')
                """,
                username,
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
        );
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
        String fileHash = token.replace("-", "").repeat(2);
        jdbcTemplate.update(
                """
                INSERT INTO import_jobs (
                    original_file_name,
                    file_hash,
                    file_size_bytes,
                    status,
                    total_rows,
                    success_rows,
                    failed_rows,
                    duplicate_rows,
                    created_by
                )
                VALUES (?, ?, 1, 'SUCCESS', 1, 1, 0, 0, ?)
                """,
                token + ".csv",
                fileHash,
                userId
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM import_jobs WHERE created_by = ?",
                Long.class,
                userId
        );
    }
}
