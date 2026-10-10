package com.finguard.core.reconciliation.service;

import com.finguard.core.reconciliation.dto.ReconciliationJobQueryRequest;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.reconciliation.dto.ReconciliationResultQueryRequest;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.reconciliation.vo.ReconciliationResultResponse;

public interface ReconciliationJobService {

    ReconciliationJobResponse create(
            Long importJobId,
            Long createdBy
    );

    ReconciliationJobResponse getById(Long reconciliationJobId);

    PageResponse<ReconciliationResultResponse> queryResults(
            Long reconciliationJobId,
            ReconciliationResultQueryRequest request
    );
    PageResponse<ReconciliationJobResponse> query(ReconciliationJobQueryRequest request);
}
