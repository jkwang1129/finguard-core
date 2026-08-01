package com.finguard.core.reconciliation.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.reconciliation.dto.ReconciliationResultQueryRequest;
import com.finguard.core.reconciliation.entity.ReconciliationJob;
import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.mapper.ReconciliationResultMapper;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.reconciliation.vo.ReconciliationResultResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReconciliationJobServiceImpl
        implements ReconciliationJobService {

    private final ReconciliationJobTransactionService transactionService;
    private final ReconciliationResultMapper reconciliationResultMapper;

    public ReconciliationJobServiceImpl(
            ReconciliationJobTransactionService transactionService,
            ReconciliationResultMapper reconciliationResultMapper) {
        this.transactionService = transactionService;
        this.reconciliationResultMapper = reconciliationResultMapper;
    }

    @Override
    public ReconciliationJobResponse create(
            Long importJobId,
            Long createdBy) {
        if (createdBy == null || createdBy <= 0) {
            throw new IllegalArgumentException(
                    "createdBy must be a positive user id"
            );
        }
        ReconciliationJob existing =
                transactionService.findByImportJobId(importJobId);
        if (existing != null) {
            return toResponse(existing, true);
        }

        ReconciliationJob created;
        try {
            created = transactionService.createPending(
                    importJobId,
                    createdBy
            );
        } catch (DuplicateKeyException exception) {
            ReconciliationJob raced =
                    transactionService.findByImportJobId(importJobId);
            if (raced == null) {
                throw exception;
            }
            return toResponse(raced, true);
        }

        return toResponse(
                transactionService.requireById(created.getId()),
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationJobResponse getById(
            Long reconciliationJobId) {
        return toResponse(
                transactionService.requireById(reconciliationJobId),
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationResultResponse> queryResults(
            Long reconciliationJobId,
            ReconciliationResultQueryRequest request) {
        transactionService.requireById(reconciliationJobId);
        IPage<ReconciliationResult> page =
                reconciliationResultMapper.selectPageByJobId(
                        new Page<>(request.page(), request.size()),
                        reconciliationJobId,
                        request.resultType()
                );
        List<ReconciliationResultResponse> records = page.getRecords()
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                records
        );
    }

    private ReconciliationJobResponse toResponse(
            ReconciliationJob job,
            boolean duplicateRequest) {
        return new ReconciliationJobResponse(
                job.getId(),
                job.getImportJobId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getMatchedCount(),
                job.getUnmatchedCount(),
                job.getDuplicateCount(),
                job.getSuspiciousCount(),
                job.getErrorSummary(),
                job.getCreatedBy(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                duplicateRequest
        );
    }

    private ReconciliationResultResponse toResponse(
            ReconciliationResult result) {
        return new ReconciliationResultResponse(
                result.getId(),
                result.getReconciliationJobId(),
                result.getCsvTransactionId(),
                result.getManualTransactionId(),
                result.getResultType(),
                result.getMatchMethod(),
                result.getReasonCode(),
                result.getCreatedAt()
        );
    }
}
