package com.finguard.core.account.controller;

import com.finguard.core.account.dto.AccountQueryRequest;
import com.finguard.core.account.dto.CreateAccountRequest;
import com.finguard.core.account.exception.AccountNotFoundException;
import com.finguard.core.account.exception.DuplicateAccountNoException;
import com.finguard.core.account.exception.InvalidAccountInputException;
import com.finguard.core.account.exception.InvalidAccountOperationException;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.account.model.AccountType;
import com.finguard.core.account.service.AccountService;
import com.finguard.core.account.vo.AccountResponse;
import com.finguard.core.common.exception.GlobalExceptionHandler;
import com.finguard.core.common.vo.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest {

    private AccountService accountService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void queryShouldBindFiltersAndReturnPageResponse() throws Exception {
        AccountResponse account = new AccountResponse(
                2L,
                "BANK_MAIN",
                "工资卡",
                AccountType.BANK,
                "CNY",
                AccountStatus.ACTIVE,
                null,
                null
        );
        when(accountService.query(any(AccountQueryRequest.class)))
                .thenReturn(new PageResponse<>(
                        2,
                        5,
                        6,
                        2,
                        List.of(account)
                ));

        mockMvc.perform(get("/api/accounts")
                        .param("page", "2")
                        .param("size", "5")
                        .param("status", "ACTIVE")
                        .param("accountType", "BANK")
                        .param("keyword", "工资"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.records[0].accountNo")
                        .value("BANK_MAIN"));

        ArgumentCaptor<AccountQueryRequest> captor =
                ArgumentCaptor.forClass(AccountQueryRequest.class);
        verify(accountService).query(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(2L);
        assertThat(captor.getValue().size()).isEqualTo(5L);
        assertThat(captor.getValue().status())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(captor.getValue().accountType())
                .isEqualTo(AccountType.BANK);
        assertThat(captor.getValue().keyword()).isEqualTo("工资");
    }

    @Test
    void queryShouldApplyDefaultPagination() throws Exception {
        when(accountService.query(any(AccountQueryRequest.class)))
                .thenReturn(new PageResponse<>(
                        1,
                        20,
                        0,
                        0,
                        List.of()
                ));

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<AccountQueryRequest> captor =
                ArgumentCaptor.forClass(AccountQueryRequest.class);
        verify(accountService).query(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1L);
        assertThat(captor.getValue().size()).isEqualTo(20L);
    }

    @Test
    void queryShouldRejectPageSizeAboveLimit() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void createShouldReturnCreatedResponseForValidRequest() throws Exception {
        AccountResponse response = new AccountResponse(
                1L,
                "CASH_MAIN",
                "日常现金",
                AccountType.CASH,
                "CNY",
                AccountStatus.ACTIVE,
                null,
                null
        );
        when(accountService.create(any(CreateAccountRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNo": "cash_main",
                                  "accountName": "日常现金",
                                  "accountType": "CASH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNo").value("CASH_MAIN"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createShouldReturnBadRequestForBlankAccountNumber() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNo": "   ",
                                  "accountName": "日常现金",
                                  "accountType": "CASH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path")
                        .value("/api/accounts"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("accountNo"));

        verifyNoInteractions(accountService);
    }

    @Test
    void getByIdShouldReturnUnifiedNotFoundError() throws Exception {
        when(accountService.getById(99L))
                .thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(get("/api/accounts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code")
                        .value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Account not found: 99"))
                .andExpect(jsonPath("$.path")
                        .value("/api/accounts/99"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void getByIdShouldReturnBadRequestForNonPositiveId() throws Exception {
        when(accountService.getById(0L))
                .thenThrow(new InvalidAccountInputException(
                        "Account id must be positive"
                ));

        mockMvc.perform(get("/api/accounts/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ACCOUNT_INPUT"))
                .andExpect(jsonPath("$.message")
                        .value("Account id must be positive"));
    }

    @Test
    void createShouldReturnConflictForDuplicateAccountNumber()
            throws Exception {
        when(accountService.create(any(CreateAccountRequest.class)))
                .thenThrow(new DuplicateAccountNoException("CASH_MAIN"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNo": "CASH_MAIN",
                                  "accountName": "日常现金",
                                  "accountType": "CASH"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_ACCOUNT_NO"));
    }

    @Test
    void deleteShouldReturnConflictForInvalidAccountState()
            throws Exception {
        doThrow(new InvalidAccountOperationException(
                "Only a disabled account can be deleted"
        )).when(accountService).delete(1L);

        mockMvc.perform(delete("/api/accounts/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ACCOUNT_OPERATION"));
    }

    @Test
    void unexpectedErrorShouldReturnGenericMessage() throws Exception {
        when(accountService.getById(1L))
                .thenThrow(new RuntimeException(
                        "sensitive database detail"
                ));

        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected internal error occurred"));
    }

    @Test
    void createShouldReturnBadRequestForMissingAccountType() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNo": "CASH_MAIN",
                                  "accountName": "日常现金"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void updateNameShouldReturnBadRequestForBlankName() throws Exception {
        mockMvc.perform(patch("/api/accounts/1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void updateStatusShouldReturnBadRequestForMissingStatus() throws Exception {
        mockMvc.perform(patch("/api/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void deleteShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/accounts/1"))
                .andExpect(status().isNoContent());

        verify(accountService).delete(1L);
    }
}
