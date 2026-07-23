package com.finguard.core.transaction.controller;

import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.finguard.core.transaction.service.TransactionService;
import com.finguard.core.transaction.vo.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest {

    private TransactionService transactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TransactionController(transactionService)
                )
                .setValidator(validator)
                .build();
    }

    @Test
    void createShouldReturnCreatedResponseForValidRequest() throws Exception {
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenReturn(transactionResponse());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson("128.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.externalTransactionNo")
                        .value("TX-001"))
                .andExpect(jsonPath("$.direction").value("EXPENSE"))
                .andExpect(jsonPath("$.source").value("MANUAL"));

        verify(transactionService).create(
                any(CreateTransactionRequest.class)
        );
    }

    @Test
    void createShouldReturnBadRequestForBlankExternalNumber()
            throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 1,
                                  "externalTransactionNo": "   ",
                                  "direction": "EXPENSE",
                                  "amount": 128.50,
                                  "transactionTime": "2026-07-23T09:00:00",
                                  "description": "午餐"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1.00",
            "12.345",
            "100000000000000000.00"
    })
    void createShouldReturnBadRequestForInvalidAmount(String amount)
            throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson(amount)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void createShouldReturnBadRequestForMissingRequiredFields()
            throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 1,
                                  "externalTransactionNo": "TX-001",
                                  "amount": 128.50,
                                  "description": "午餐"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void getByIdShouldReturnExistingTransaction() throws Exception {
        when(transactionService.getById(10L))
                .thenReturn(transactionResponse());

        mockMvc.perform(get("/api/transactions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.amount").value(128.50))
                .andExpect(jsonPath("$.description").value("午餐"));

        verify(transactionService).getById(10L);
    }

    @Test
    void updateShouldReturnUpdatedTransactionForValidRequest()
            throws Exception {
        TransactionResponse response = new TransactionResponse(
                10L,
                1L,
                "TX-001",
                TransactionDirection.INCOME,
                new BigDecimal("200.00"),
                LocalDateTime.of(2026, 7, 23, 9, 30),
                "退款",
                TransactionSource.MANUAL,
                null,
                null
        );
        when(transactionService.update(
                org.mockito.ArgumentMatchers.eq(10L),
                any(UpdateTransactionRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "INCOME",
                                  "amount": 200.00,
                                  "transactionTime": "2026-07-23T09:30:00",
                                  "description": "退款"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("INCOME"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.description").value("退款"));

        verify(transactionService).update(
                org.mockito.ArgumentMatchers.eq(10L),
                any(UpdateTransactionRequest.class)
        );
    }

    @Test
    void updateShouldReturnBadRequestForMissingAmount() throws Exception {
        mockMvc.perform(put("/api/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "INCOME",
                                  "transactionTime": "2026-07-23T09:30:00",
                                  "description": "退款"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void updateShouldReturnBadRequestForTooManyDecimalPlaces()
            throws Exception {
        mockMvc.perform(put("/api/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "INCOME",
                                  "amount": 12.345,
                                  "transactionTime": "2026-07-23T09:30:00",
                                  "description": "退款"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void deleteShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/transactions/10"))
                .andExpect(status().isNoContent());

        verify(transactionService).delete(10L);
    }

    private String validCreateJson(String amount) {
        return """
                {
                  "accountId": 1,
                  "externalTransactionNo": "TX-001",
                  "direction": "EXPENSE",
                  "amount": %s,
                  "transactionTime": "2026-07-23T09:00:00",
                  "description": "午餐"
                }
                """.formatted(amount);
    }

    private TransactionResponse transactionResponse() {
        return new TransactionResponse(
                10L,
                1L,
                "TX-001",
                TransactionDirection.EXPENSE,
                new BigDecimal("128.50"),
                LocalDateTime.of(2026, 7, 23, 9, 0),
                "午餐",
                TransactionSource.MANUAL,
                null,
                null
        );
    }
}
