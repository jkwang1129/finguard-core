package com.finguard.core.importjob.service.impl;

import com.finguard.core.importjob.entity.ImportJob;
import com.finguard.core.importjob.entity.ImportJobFile;
import com.finguard.core.importjob.entity.ImportRowError;
import com.finguard.core.importjob.exception.ImportFileParseException;
import com.finguard.core.importjob.exception.ImportJobNotFoundException;
import com.finguard.core.importjob.mapper.ImportJobMapper;
import com.finguard.core.importjob.mapper.ImportJobFileMapper;
import com.finguard.core.importjob.mapper.ImportRowErrorMapper;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.importjob.model.ImportProcessingResult;
import com.finguard.core.importjob.model.ImportRowErrorCode;
import com.finguard.core.importjob.parser.CsvImportFileParser;
import com.finguard.core.importjob.parser.ParsedImportFile;
import com.finguard.core.importjob.parser.PreparedImportFile;
import com.finguard.core.importjob.validation.CsvImportRowValidator;
import com.finguard.core.importjob.validation.ImportFileValidationResult;
import com.finguard.core.importjob.validation.ImportRowValidationError;
import com.finguard.core.importjob.validation.ValidatedImportRow;
import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.mapper.OutboxEventMapper;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.messaging.outbox.model.OutboxStatus;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.mapper.TransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportJobTransactionService {

    static final int WRITE_BATCH_SIZE = 500;

    private static final String TRANSACTION_UNIQUE_CONSTRAINT =
            "uk_transactions_account_source_external_no";
    private static final String PROCESSING_FAILED_MESSAGE =
            "Import processing failed";

    private final ImportJobMapper importJobMapper;
    private final ImportJobFileMapper importJobFileMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ImportRowErrorMapper importRowErrorMapper;
    private final TransactionMapper transactionMapper;
    private final CsvImportFileParser fileParser;
    private final CsvImportRowValidator rowValidator;
    private final Clock businessClock;

    public ImportJobTransactionService(
            ImportJobMapper importJobMapper,
            ImportJobFileMapper importJobFileMapper,
            OutboxEventMapper outboxEventMapper,
            ImportRowErrorMapper importRowErrorMapper,
            TransactionMapper transactionMapper,
            CsvImportFileParser fileParser,
            CsvImportRowValidator rowValidator,
            Clock businessClock) {
        this.importJobMapper = importJobMapper;
        this.importJobFileMapper = importJobFileMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.importRowErrorMapper = importRowErrorMapper;
        this.transactionMapper = transactionMapper;
        this.fileParser = fileParser;
        this.rowValidator = rowValidator;
        this.businessClock = businessClock;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public ImportJob findByFileHash(String fileHash) {
        return importJobMapper.findByFileHash(fileHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportJob createPending(
            PreparedImportFile preparedFile,
            Long createdBy) {
        ImportJob importJob = new ImportJob();
        importJob.setOriginalFileName(preparedFile.originalFileName());
        importJob.setFileHash(preparedFile.fileHash());
        importJob.setFileSizeBytes(preparedFile.fileSizeBytes());
        importJob.setStatus(ImportJobStatus.PENDING);
        importJob.setTotalRows(0);
        importJob.setSuccessRows(0);
        importJob.setFailedRows(0);
        importJob.setDuplicateRows(0);
        importJob.setCreatedBy(createdBy);
        int inserted = importJobMapper.insert(importJob);
        if (inserted != 1 || importJob.getId() == null) {
            throw new IllegalStateException(
                    "Import job could not be created"
            );
        }

        ImportJobFile importJobFile = new ImportJobFile();
        importJobFile.setImportJobId(importJob.getId());
        importJobFile.setContent(preparedFile.originalBytes());
        importJobFile.setContentLength(
                Math.toIntExact(preparedFile.fileSizeBytes())
        );
        if (importJobFileMapper.insert(importJobFile) != 1) {
            throw new IllegalStateException(
                    "Import file could not be persisted"
            );
        }

        OutboxEvent event = new OutboxEvent();
        event.setEventType(OutboxEventType.IMPORT_REQUESTED);
        event.setAggregateId(importJob.getId());
        event.setSchemaVersion(1);
        event.setStatus(OutboxStatus.NEW);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now(businessClock));
        if (outboxEventMapper.insert(event) != 1
                || event.getId() == null) {
            throw new IllegalStateException(
                    "Import outbox event could not be created"
            );
        }
        return importJob;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long importJobId) {
        int updated = importJobMapper.markProcessing(
                importJobId,
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, importJobId);
    }

    @Transactional
    public ImportProcessingResult processPending(Long importJobId) {
        if (importJobId == null || importJobId <= 0) {
            throw new IllegalArgumentException(
                    "importJobId must be a positive number"
            );
        }
        ImportJob importJob = importJobMapper.findByIdForUpdate(importJobId);
        if (importJob == null) {
            throw new ImportJobNotFoundException(importJobId);
        }
        if (isTerminal(importJob.getStatus())) {
            return ImportProcessingResult.ALREADY_COMPLETED;
        }
        PreparedImportFile preparedFile = restorePreparedFile(importJob);
        ImportProcessingResult result;
        if (importJob.getStatus() == ImportJobStatus.PENDING) {
            int updated = importJobMapper.markProcessing(
                    importJobId,
                    LocalDateTime.now(businessClock)
            );
            requireSingleStateUpdate(updated, importJobId);
            result = ImportProcessingResult.PROCESSED;
        } else if (importJob.getStatus() == ImportJobStatus.PROCESSING) {
            result = ImportProcessingResult.RECOVERED;
        } else {
            throw new IllegalStateException(
                    "Import job status is not supported: "
                            + importJob.getStatus()
            );
        }
        processAndComplete(importJobId, preparedFile);
        return result;
    }

    @Transactional
    public void processAndComplete(
            Long importJobId,
            PreparedImportFile preparedFile) {
        ParsedImportFile parsedFile;
        try {
            parsedFile = fileParser.parse(preparedFile);
        } catch (ImportFileParseException exception) {
            completeFileFailure(importJobId, exception);
            return;
        }

        ImportFileValidationResult validationResult =
                rowValidator.validate(parsedFile);
        List<ImportRowValidationError> errors =
                new ArrayList<>(validationResult.errors());
        int successRows = persistTransactions(
                importJobId,
                validationResult.validRows(),
                errors
        );
        persistRowErrors(importJobId, errors);

        int failedRows = distinctFailedRows(errors);
        int duplicateRows = distinctDuplicateRows(errors);
        ImportJobStatus status = terminalStatus(
                successRows,
                failedRows
        );
        String errorSummary = failedRows == 0
                ? null
                : failedRows + (failedRows == 1
                ? " row rejected"
                : " rows rejected");

        int updated = importJobMapper.completeProcessing(
                importJobId,
                status,
                validationResult.totalRows(),
                successRows,
                failedRows,
                duplicateRows,
                null,
                errorSummary,
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, importJobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessingFailed(Long importJobId) {
        int updated = importJobMapper.completeProcessing(
                importJobId,
                ImportJobStatus.FAILED,
                0,
                0,
                0,
                0,
                ImportFileErrorCode.PROCESSING_FAILED,
                PROCESSING_FAILED_MESSAGE,
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, importJobId);
    }

    @Transactional(readOnly = true)
    public ImportJob requireById(Long importJobId) {
        ImportJob importJob = importJobMapper.selectById(importJobId);
        if (importJob == null) {
            throw new ImportJobNotFoundException(importJobId);
        }
        return importJob;
    }

    private int persistTransactions(
            Long importJobId,
            List<ValidatedImportRow> validRows,
            List<ImportRowValidationError> errors) {
        int successRows = 0;
        for (int offset = 0;
             offset < validRows.size();
             offset += WRITE_BATCH_SIZE) {
            List<ValidatedImportRow> batch = validRows.subList(
                    offset,
                    Math.min(offset + WRITE_BATCH_SIZE, validRows.size())
            );
            successRows += persistTransactionBatch(
                    importJobId,
                    batch,
                    errors
            );
        }
        return successRows;
    }

    private PreparedImportFile restorePreparedFile(ImportJob importJob) {
        ImportJobFile importJobFile = importJobFileMapper.selectById(
                importJob.getId()
        );
        if (importJobFile == null) {
            throw new IllegalStateException(
                    "Persisted import file is missing"
            );
        }
        byte[] content = importJobFile.getContent();
        if (content == null
                || importJobFile.getContentLength() == null
                || importJobFile.getContentLength() != content.length
                || importJob.getFileSizeBytes() == null
                || importJob.getFileSizeBytes() != content.length) {
            throw new IllegalStateException(
                    "Persisted import file length is inconsistent"
            );
        }
        PreparedImportFile preparedFile = fileParser.prepare(
                importJob.getOriginalFileName(),
                content
        );
        if (!preparedFile.fileHash().equals(importJob.getFileHash())) {
            throw new IllegalStateException(
                    "Persisted import file hash is inconsistent"
            );
        }
        return preparedFile;
    }

    private int persistTransactionBatch(
            Long importJobId,
            List<ValidatedImportRow> batch,
            List<ImportRowValidationError> errors) {
        List<Transaction> transactions = batch.stream()
                .map(row -> toTransaction(importJobId, row))
                .toList();
        TransactionStatus status =
                TransactionAspectSupport.currentTransactionStatus();
        Object batchSavepoint = status.createSavepoint();
        try {
            int inserted = transactionMapper.insertBatch(transactions);
            if (inserted != transactions.size()) {
                throw new IllegalStateException(
                        "Transaction batch insert count was inconsistent"
                );
            }
            status.releaseSavepoint(batchSavepoint);
            return inserted;
        } catch (DuplicateKeyException exception) {
            if (!isTransactionBusinessKeyConflict(exception)) {
                throw exception;
            }
            status.rollbackToSavepoint(batchSavepoint);
            status.releaseSavepoint(batchSavepoint);
            return replayIndividually(
                    status,
                    importJobId,
                    batch,
                    errors
            );
        }
    }

    private int replayIndividually(
            TransactionStatus status,
            Long importJobId,
            List<ValidatedImportRow> batch,
            List<ImportRowValidationError> errors) {
        int inserted = 0;
        for (ValidatedImportRow row : batch) {
            Object rowSavepoint = status.createSavepoint();
            try {
                int rowCount = transactionMapper.insert(
                        toTransaction(importJobId, row)
                );
                if (rowCount != 1) {
                    throw new IllegalStateException(
                            "Transaction insert count was inconsistent"
                    );
                }
                status.releaseSavepoint(rowSavepoint);
                inserted++;
            } catch (DuplicateKeyException exception) {
                status.rollbackToSavepoint(rowSavepoint);
                status.releaseSavepoint(rowSavepoint);
                if (!isTransactionBusinessKeyConflict(exception)) {
                    throw exception;
                }
                errors.add(ImportRowValidationError.of(
                        row.rowNumber(),
                        "external_transaction_no",
                        ImportRowErrorCode.DUPLICATE_TRANSACTION,
                        row.externalTransactionNo(),
                        "Transaction already exists"
                ));
            }
        }
        return inserted;
    }

    private void persistRowErrors(
            Long importJobId,
            List<ImportRowValidationError> errors) {
        for (int offset = 0;
             offset < errors.size();
             offset += WRITE_BATCH_SIZE) {
            List<ImportRowError> batch = errors.subList(
                    offset,
                    Math.min(offset + WRITE_BATCH_SIZE, errors.size())
            ).stream().map(error -> toEntity(importJobId, error)).toList();
            int inserted = importRowErrorMapper.insertBatch(batch);
            if (inserted != batch.size()) {
                throw new IllegalStateException(
                        "Import row error batch insert count was inconsistent"
                );
            }
        }
    }

    private void completeFileFailure(
            Long importJobId,
            ImportFileParseException exception) {
        int updated = importJobMapper.completeProcessing(
                importJobId,
                ImportJobStatus.FAILED,
                0,
                0,
                0,
                0,
                exception.getErrorCode(),
                exception.getMessage(),
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, importJobId);
    }

    private ImportJobStatus terminalStatus(
            int successRows,
            int failedRows) {
        if (successRows == 0) {
            return ImportJobStatus.FAILED;
        }
        return failedRows == 0
                ? ImportJobStatus.SUCCESS
                : ImportJobStatus.PARTIAL_SUCCESS;
    }

    private boolean isTerminal(ImportJobStatus status) {
        return status == ImportJobStatus.SUCCESS
                || status == ImportJobStatus.PARTIAL_SUCCESS
                || status == ImportJobStatus.FAILED;
    }

    private int distinctFailedRows(
            List<ImportRowValidationError> errors) {
        return (int) errors.stream()
                .map(ImportRowValidationError::rowNumber)
                .distinct()
                .count();
    }

    private int distinctDuplicateRows(
            List<ImportRowValidationError> errors) {
        return (int) errors.stream()
                .filter(error ->
                        error.errorCode()
                                == ImportRowErrorCode
                                .DUPLICATE_TRANSACTION_IN_FILE
                                || error.errorCode()
                                == ImportRowErrorCode.DUPLICATE_TRANSACTION
                )
                .map(ImportRowValidationError::rowNumber)
                .distinct()
                .count();
    }

    private Transaction toTransaction(
            Long importJobId,
            ValidatedImportRow row) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(row.accountId());
        transaction.setImportJobId(importJobId);
        transaction.setExternalTransactionNo(
                row.externalTransactionNo()
        );
        transaction.setDirection(row.direction());
        transaction.setAmount(row.amount());
        transaction.setTransactionTime(row.transactionTime());
        transaction.setDescription(row.description());
        transaction.setSource(row.source());
        transaction.setDeleted(false);
        return transaction;
    }

    private ImportRowError toEntity(
            Long importJobId,
            ImportRowValidationError error) {
        ImportRowError entity = new ImportRowError();
        entity.setImportJobId(importJobId);
        entity.setRowNumber(error.rowNumber());
        entity.setField(error.fieldName());
        entity.setErrorCode(error.errorCode());
        entity.setRejectedValue(error.rejectedValue());
        entity.setMessage(error.message());
        return entity;
    }

    private boolean isTransactionBusinessKeyConflict(
            DuplicateKeyException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains(TRANSACTION_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void requireSingleStateUpdate(
            int updated,
            Long importJobId) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Import job state transition was rejected: "
                            + importJobId
            );
        }
    }
}
