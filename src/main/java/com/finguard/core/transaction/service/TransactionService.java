package com.finguard.core.transaction.service;

import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.vo.TransactionResponse;

public interface TransactionService {

    TransactionResponse create(CreateTransactionRequest request);

    TransactionResponse getById(Long transactionId);

    TransactionResponse update(
            Long transactionId,
            UpdateTransactionRequest request
    );

    void delete(Long transactionId);
}
