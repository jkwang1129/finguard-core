package com.finguard.core.transaction.controller;

import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.TransactionQueryRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.service.TransactionService;
import com.finguard.core.transaction.vo.TransactionResponse;
import com.finguard.core.common.vo.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse getById(
            @PathVariable @Positive Long transactionId) {
        return transactionService.getById(transactionId);
    }

    @GetMapping
    public PageResponse<TransactionResponse> query(
            @Valid @ModelAttribute TransactionQueryRequest request) {
        return transactionService.query(request);
    }

    @PutMapping("/{transactionId}")
    public TransactionResponse update(
            @PathVariable @Positive Long transactionId,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return transactionService.update(transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long transactionId) {
        transactionService.delete(transactionId);
        return ResponseEntity.noContent().build();
    }
}
