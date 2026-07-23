package com.finguard.core.transaction.service;

import com.finguard.core.transaction.dto.CreateTransactionRequest;
import com.finguard.core.transaction.dto.TransactionQueryRequest;
import com.finguard.core.transaction.dto.UpdateTransactionRequest;
import com.finguard.core.transaction.vo.TransactionResponse;
import com.finguard.core.common.vo.PageResponse;

public interface TransactionService {

    TransactionResponse create(CreateTransactionRequest request);

    TransactionResponse getById(Long transactionId);

    PageResponse<TransactionResponse> query(TransactionQueryRequest request);

    TransactionResponse update(
            Long transactionId,
            UpdateTransactionRequest request
    );

    void delete(Long transactionId);
}
