package com.finguard.core.account.controller;

import com.finguard.core.account.dto.CreateAccountRequest;
import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.account.model.AccountType;
import com.finguard.core.account.service.AccountService;
import com.finguard.core.account.vo.AccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
                .setValidator(validator)
                .build();
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
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
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
