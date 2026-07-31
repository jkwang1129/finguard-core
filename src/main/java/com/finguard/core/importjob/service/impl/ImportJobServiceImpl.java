package com.finguard.core.importjob.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.importjob.dto.ImportRowErrorQueryRequest;
import com.finguard.core.importjob.entity.ImportJob;
import com.finguard.core.importjob.entity.ImportRowError;
import com.finguard.core.importjob.exception.ImportJobNotFoundException;
import com.finguard.core.importjob.mapper.ImportRowErrorMapper;
import com.finguard.core.importjob.parser.CsvImportFileParser;
import com.finguard.core.importjob.parser.PreparedImportFile;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.importjob.vo.ImportRowErrorResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImportJobServiceImpl implements ImportJobService {

    private final CsvImportFileParser fileParser;
    private final ImportJobTransactionService transactionService;
    private final ImportRowErrorMapper importRowErrorMapper;

    public ImportJobServiceImpl(
            CsvImportFileParser fileParser,
            ImportJobTransactionService transactionService,
            ImportRowErrorMapper importRowErrorMapper) {
        this.fileParser = fileParser;
        this.transactionService = transactionService;
        this.importRowErrorMapper = importRowErrorMapper;
    }

    @Override
    public ImportJobResponse upload(
            String originalFileName,
            byte[] originalBytes,
            Long createdBy) {
        if (createdBy == null || createdBy <= 0) {
            throw new IllegalArgumentException(
                    "createdBy must be a positive user id"
            );
        }
        PreparedImportFile preparedFile = fileParser.prepare(
                originalFileName,
                originalBytes
        );
        ImportJob existing = transactionService.findByFileHash(
                preparedFile.fileHash()
        );
        if (existing != null) {
            return toResponse(existing, true);
        }

        ImportJob created;
        try {
            created = transactionService.createPending(
                    preparedFile,
                    createdBy
            );
        } catch (DuplicateKeyException exception) {
            ImportJob raced = transactionService.findByFileHash(
                    preparedFile.fileHash()
            );
            if (raced == null) {
                throw exception;
            }
            return toResponse(raced, true);
        }

        transactionService.markProcessing(created.getId());
        try {
            transactionService.processAndComplete(
                    created.getId(),
                    preparedFile
            );
        } catch (RuntimeException exception) {
            try {
                transactionService.markProcessingFailed(created.getId());
            } catch (RuntimeException recoveryException) {
                exception.addSuppressed(recoveryException);
            }
            throw exception;
        }
        return toResponse(
                transactionService.requireById(created.getId()),
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse getById(Long importJobId) {
        return toResponse(requireJob(importJobId), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ImportRowErrorResponse> queryErrors(
            Long importJobId,
            ImportRowErrorQueryRequest request) {
        requireJob(importJobId);
        IPage<ImportRowError> page =
                importRowErrorMapper.selectPageByImportJobId(
                        new Page<>(request.page(), request.size()),
                        importJobId
                );
        List<ImportRowErrorResponse> records = page.getRecords()
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

    private ImportJob requireJob(Long importJobId) {
        try {
            return transactionService.requireById(importJobId);
        } catch (ImportJobNotFoundException exception) {
            throw exception;
        }
    }

    private ImportJobResponse toResponse(
            ImportJob importJob,
            boolean duplicateFile) {
        return new ImportJobResponse(
                importJob.getId(),
                importJob.getOriginalFileName(),
                importJob.getFileHash(),
                importJob.getFileSizeBytes(),
                importJob.getStatus(),
                importJob.getTotalRows(),
                importJob.getSuccessRows(),
                importJob.getFailedRows(),
                importJob.getDuplicateRows(),
                importJob.getFileErrorCode(),
                importJob.getErrorSummary(),
                importJob.getCreatedBy(),
                importJob.getStartedAt(),
                importJob.getFinishedAt(),
                importJob.getCreatedAt(),
                importJob.getUpdatedAt(),
                duplicateFile
        );
    }

    private ImportRowErrorResponse toResponse(ImportRowError error) {
        return new ImportRowErrorResponse(
                error.getId(),
                error.getImportJobId(),
                error.getRowNumber(),
                error.getField(),
                error.getErrorCode(),
                error.getRejectedValue(),
                error.getMessage(),
                error.getCreatedAt()
        );
    }
}
