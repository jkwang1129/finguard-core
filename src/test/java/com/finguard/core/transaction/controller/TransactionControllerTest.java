package com.finguard.core.transaction.controller;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.common.exception.GlobalExceptionHandler;
import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.TransactionQueryRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.exception.DuplicateTransactionException;
import com.finguard.core.transaction.exception.InvalidTransactionInputException;
import com.finguard.core.transaction.exception.TransactionNotFoundException;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import com.finguard.core.transaction.service.TransactionService;
import com.finguard.core.transaction.vo.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void queryShouldBindFiltersAndReturnPageResponse() throws Exception {
        when(transactionService.query(
                any(TransactionQueryRequest.class)
        )).thenReturn(new PageResponse<>(
                1,
                10,
                1,
                1,
                List.of(transactionResponse())
        ));

        mockMvc.perform(get("/api/transactions")
                        .param("page", "1")
                        .param("size", "10")
                        .param("accountId", "1")
                        .param("direction", "EXPENSE")
                        .param("source", "MANUAL")
                        .param("externalTransactionNo", "TX-001")
                        .param("startTime", "2026-07-01T00:00:00")
                        .param("endTime", "2026-07-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].id").value(10))
                .andExpect(jsonPath("$.records[0].source")
                        .value("MANUAL"));

        ArgumentCaptor<TransactionQueryRequest> captor =
                ArgumentCaptor.forClass(TransactionQueryRequest.class);
        verify(transactionService).query(captor.capture());
        TransactionQueryRequest request = captor.getValue();
        assertThat(request.page()).isEqualTo(1L);
        assertThat(request.size()).isEqualTo(10L);
        assertThat(request.accountId()).isEqualTo(1L);
        assertThat(request.direction())
                .isEqualTo(TransactionDirection.EXPENSE);
        assertThat(request.source())
                .isEqualTo(TransactionSource.MANUAL);
        assertThat(request.startTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(request.endTime())
                .isEqualTo(LocalDateTime.of(
                        2026,
                        7,
                        31,
                        23,
                        59,
                        59
                ));
    }

    @Test
    void queryShouldRejectNonPositiveAccountId() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("accountId"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void queryShouldReturnBadRequestForReversedTimeRange()
            throws Exception {
        when(transactionService.query(
                any(TransactionQueryRequest.class)
        )).thenThrow(new InvalidTransactionInputException(
                "Start time must not be later than end time"
        ));

        mockMvc.perform(get("/api/transactions")
                        .param("startTime", "2026-07-24T00:00:00")
                        .param("endTime", "2026-07-23T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_TRANSACTION_INPUT"))
                .andExpect(jsonPath("$.message")
                        .value("Start time must not be later than end time"));
    }

    @Test
    void getByIdShouldReturnUnifiedNotFoundError() throws Exception {
        when(transactionService.getById(99L))
                .thenThrow(new TransactionNotFoundException(99L));

        mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.path")
                        .value("/api/transactions/99"));
    }

    @Test
    void createShouldReturnConflictForDuplicateTransaction()
            throws Exception {
        when(transactionService.create(
                any(CreateTransactionRequest.class)
        )).thenThrow(new DuplicateTransactionException(
                1L,
                TransactionSource.MANUAL,
                "TX-001"
        ));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson("128.50")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_TRANSACTION"));
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

    @Test
    void createShouldReturnInvalidRequestForUnknownDirection()
            throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 1,
                                  "externalTransactionNo": "TX-001",
                                  "direction": "UNKNOWN",
                                  "amount": 128.50,
                                  "transactionTime": "2026-07-23T09:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

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
