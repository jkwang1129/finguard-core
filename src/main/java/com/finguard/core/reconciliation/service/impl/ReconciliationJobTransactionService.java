package com.finguard.core.reconciliation.service.impl;

import com.finguard.core.importjob.entity.ImportJob;
import com.finguard.core.importjob.exception.ImportJobNotFoundException;
import com.finguard.core.importjob.mapper.ImportJobMapper;
import com.finguard.core.importjob.model.ImportJobStatus;
import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import com.finguard.core.messaging.outbox.mapper.OutboxEventMapper;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.messaging.outbox.model.OutboxStatus;
import com.finguard.core.reconciliation.entity.ReconciliationJob;
import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.exception.InvalidReconciliationOperationException;
import com.finguard.core.reconciliation.exception.ReconciliationJobNotFoundException;
import com.finguard.core.reconciliation.mapper.ReconciliationJobMapper;
import com.finguard.core.reconciliation.mapper.ReconciliationResultMapper;
import com.finguard.core.reconciliation.model.ReconciliationDecision;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import com.finguard.core.reconciliation.model.ReconciliationProcessingResult;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.reconciliation.service.ReconciliationMatcher;
import com.finguard.core.review.service.ReviewTaskGenerator;
import com.finguard.core.risk.entity.RiskHit;
import com.finguard.core.risk.service.RiskEvaluationService;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.mapper.TransactionMapper;
import com.finguard.core.transaction.model.TransactionBusinessKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationJobTransactionService {

    static final int BATCH_SIZE = 500;

    private static final String PROCESSING_FAILED_MESSAGE =
            "Reconciliation processing failed";
    private static final String RETRY_EXHAUSTED_MESSAGE =
            "Reconciliation retry limit reached";
    private static final String BUSINESS_FAILED_MESSAGE =
            "Reconciliation input is no longer valid";

    private final ReconciliationJobMapper reconciliationJobMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ReconciliationResultMapper reconciliationResultMapper;
    private final ImportJobMapper importJobMapper;
    private final TransactionMapper transactionMapper;
    private final ReconciliationMatcher matcher;
    private final RiskEvaluationService riskEvaluationService;
    private final ReviewTaskGenerator reviewTaskGenerator;
    private final Clock businessClock;

    public ReconciliationJobTransactionService(
            ReconciliationJobMapper reconciliationJobMapper,
            OutboxEventMapper outboxEventMapper,
            ReconciliationResultMapper reconciliationResultMapper,
            ImportJobMapper importJobMapper,
            TransactionMapper transactionMapper,
            ReconciliationMatcher matcher,
            RiskEvaluationService riskEvaluationService,
            ReviewTaskGenerator reviewTaskGenerator,
            Clock businessClock) {
        this.reconciliationJobMapper = reconciliationJobMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.reconciliationResultMapper = reconciliationResultMapper;
        this.importJobMapper = importJobMapper;
        this.transactionMapper = transactionMapper;
        this.matcher = matcher;
        this.riskEvaluationService = riskEvaluationService;
        this.reviewTaskGenerator = reviewTaskGenerator;
        this.businessClock = businessClock;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public ReconciliationJob findByImportJobId(Long importJobId) {
        return reconciliationJobMapper.findByImportJobId(importJobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconciliationJob createPending(
            Long importJobId,
            Long createdBy) {
        validateImportJob(importJobId);
        ReconciliationJob job = new ReconciliationJob();
        job.setImportJobId(importJobId);
        job.setStatus(ReconciliationJobStatus.PENDING);
        job.setTotalCount(0);
        job.setMatchedCount(0);
        job.setUnmatchedCount(0);
        job.setDuplicateCount(0);
        job.setSuspiciousCount(0);
        job.setCreatedBy(createdBy);
        int inserted = reconciliationJobMapper.insert(job);
        if (inserted != 1 || job.getId() == null) {
            throw new IllegalStateException(
                    "Reconciliation job could not be created"
            );
        }

        OutboxEvent event = new OutboxEvent();
        event.setEventType(
                OutboxEventType.RECONCILIATION_REQUESTED
        );
        event.setAggregateId(job.getId());
        event.setSchemaVersion(1);
        event.setStatus(OutboxStatus.NEW);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now(businessClock));
        if (outboxEventMapper.insert(event) != 1
                || event.getId() == null) {
            throw new IllegalStateException(
                    "Reconciliation outbox event could not be created"
            );
        }
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long reconciliationJobId) {
        int updated = reconciliationJobMapper.markProcessing(
                reconciliationJobId,
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, reconciliationJobId);
    }

    @Transactional
    public ReconciliationProcessingResult processPending(
            Long reconciliationJobId) {
        if (reconciliationJobId == null || reconciliationJobId <= 0) {
            throw new IllegalArgumentException(
                    "reconciliationJobId must be a positive number"
            );
        }
        ReconciliationJob job = reconciliationJobMapper.findByIdForUpdate(
                reconciliationJobId
        );
        if (job == null) {
            throw new ReconciliationJobNotFoundException(
                    reconciliationJobId
            );
        }
        if (isTerminal(job.getStatus())) {
            return ReconciliationProcessingResult.ALREADY_COMPLETED;
        }

        ReconciliationProcessingResult result;
        if (job.getStatus() == ReconciliationJobStatus.PENDING) {
            int updated = reconciliationJobMapper.markProcessing(
                    reconciliationJobId,
                    LocalDateTime.now(businessClock)
            );
            requireSingleStateUpdate(updated, reconciliationJobId);
            result = ReconciliationProcessingResult.PROCESSED;
        } else if (job.getStatus()
                == ReconciliationJobStatus.PROCESSING) {
            long existingResults = reconciliationResultMapper
                    .countByJobId(reconciliationJobId);
            if (existingResults != 0) {
                throw new IllegalStateException(
                        "Processing reconciliation job has persisted results"
                );
            }
            result = ReconciliationProcessingResult.RECOVERED;
        } else {
            throw new IllegalStateException(
                    "Reconciliation job status is not supported: "
                            + job.getStatus()
            );
        }

        processAndComplete(reconciliationJobId, job.getImportJobId());
        return result;
    }

    @Transactional
    public void processAndComplete(
            Long reconciliationJobId,
            Long importJobId) {
        List<Transaction> imported =
                transactionMapper.selectImportedByJobId(importJobId);
        if (imported.isEmpty()) {
            throw new IllegalStateException(
                    "Accepted import job has no transactions"
            );
        }
        List<Transaction> manual = loadManualCandidates(imported);
        List<ReconciliationTransaction> importedModels = imported.stream()
                .map(this::toModel)
                .toList();
        List<ReconciliationDecision> decisions = matcher.match(
                importedModels,
                manual.stream().map(this::toModel).toList()
        );
        persistResults(reconciliationJobId, decisions);
        List<ReconciliationResult> persistedResults =
                reconciliationResultMapper.selectByJobId(
                        reconciliationJobId
                );
        if (persistedResults.size() != decisions.size()) {
            throw new IllegalStateException(
                    "Persisted reconciliation result count was inconsistent"
            );
        }
        List<RiskHit> riskHits = riskEvaluationService.evaluateAndPersist(
                reconciliationJobId,
                persistedResults,
                importedModels
        );
        reviewTaskGenerator.generateAndPersist(
                persistedResults,
                riskHits
        );
        complete(reconciliationJobId, decisions);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessingFailed(Long reconciliationJobId) {
        int updated = reconciliationJobMapper.markFailed(
                reconciliationJobId,
                PROCESSING_FAILED_MESSAGE,
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, reconciliationJobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRetryExhausted(Long reconciliationJobId) {
        return markFailedIfNonTerminal(
                reconciliationJobId,
                RETRY_EXHAUSTED_MESSAGE
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markBusinessFailed(Long reconciliationJobId) {
        return markFailedIfNonTerminal(
                reconciliationJobId,
                BUSINESS_FAILED_MESSAGE
        );
    }

    @Transactional(readOnly = true)
    public ReconciliationJob requireById(Long reconciliationJobId) {
        ReconciliationJob job =
                reconciliationJobMapper.selectById(reconciliationJobId);
        if (job == null) {
            throw new ReconciliationJobNotFoundException(
                    reconciliationJobId
            );
        }
        return job;
    }

    private void validateImportJob(Long importJobId) {
        ImportJob importJob = importJobMapper.selectById(importJobId);
        if (importJob == null) {
            throw new ImportJobNotFoundException(importJobId);
        }
        if ((importJob.getStatus() != ImportJobStatus.SUCCESS
                && importJob.getStatus()
                != ImportJobStatus.PARTIAL_SUCCESS)
                || importJob.getSuccessRows() == null
                || importJob.getSuccessRows() <= 0) {
            throw new InvalidReconciliationOperationException(
                    "Import job is not ready for reconciliation"
            );
        }
        long actual = transactionMapper.countImportedByJobId(
                importJobId
        );
        if (actual == 0) {
            throw new InvalidReconciliationOperationException(
                    "Import job has no transactions to reconcile"
            );
        }
        if (actual != importJob.getSuccessRows()) {
            throw new IllegalStateException(
                    "Import job transaction count is inconsistent"
            );
        }
    }

    private List<Transaction> loadManualCandidates(
            List<Transaction> imported) {
        Map<Long, Transaction> candidates = new LinkedHashMap<>();
        List<TransactionBusinessKey> keys = imported.stream()
                .map(transaction -> new TransactionBusinessKey(
                        transaction.getAccountId(),
                        transaction.getExternalTransactionNo()
                ))
                .distinct()
                .toList();
        for (int offset = 0;
             offset < keys.size();
             offset += BATCH_SIZE) {
            transactionMapper.selectManualByBusinessKeys(
                    keys.subList(
                            offset,
                            Math.min(offset + BATCH_SIZE, keys.size())
                    )
            ).forEach(transaction ->
                    candidates.put(transaction.getId(), transaction)
            );
        }

        LocalDateTime fromTime = imported.stream()
                .map(Transaction::getTransactionTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow()
                .minusDays(ReconciliationMatcher.TIME_TOLERANCE_DAYS);
        LocalDateTime toTime = imported.stream()
                .map(Transaction::getTransactionTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow()
                .plusDays(ReconciliationMatcher.TIME_TOLERANCE_DAYS);
        List<Long> accountIds = imported.stream()
                .map(Transaction::getAccountId)
                .distinct()
                .sorted()
                .toList();
        for (int offset = 0;
             offset < accountIds.size();
             offset += BATCH_SIZE) {
            transactionMapper.selectManualByAccountsAndTime(
                    accountIds.subList(
                            offset,
                            Math.min(
                                    offset + BATCH_SIZE,
                                    accountIds.size()
                            )
                    ),
                    fromTime,
                    toTime
            ).forEach(transaction ->
                    candidates.put(transaction.getId(), transaction)
            );
        }
        return candidates.values().stream()
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
    }

    private void persistResults(
            Long reconciliationJobId,
            List<ReconciliationDecision> decisions) {
        for (int offset = 0;
             offset < decisions.size();
             offset += BATCH_SIZE) {
            List<ReconciliationResult> batch = decisions.subList(
                    offset,
                    Math.min(offset + BATCH_SIZE, decisions.size())
            ).stream().map(decision -> toEntity(
                    reconciliationJobId,
                    decision
            )).toList();
            int inserted = reconciliationResultMapper.insertBatch(batch);
            if (inserted != batch.size()) {
                throw new IllegalStateException(
                        "Reconciliation result batch count was inconsistent"
                );
            }
        }
    }

    private void complete(
            Long reconciliationJobId,
            List<ReconciliationDecision> decisions) {
        int matched = count(
                decisions,
                ReconciliationResultType.MATCHED
        );
        int unmatched = count(
                decisions,
                ReconciliationResultType.UNMATCHED
        );
        int duplicate = count(
                decisions,
                ReconciliationResultType.DUPLICATE
        );
        int suspicious = count(
                decisions,
                ReconciliationResultType.SUSPICIOUS
        );
        int total = decisions.size();
        if (total != matched + unmatched + duplicate + suspicious) {
            throw new IllegalStateException(
                    "Reconciliation counters are inconsistent"
            );
        }
        int updated = reconciliationJobMapper.completeProcessing(
                reconciliationJobId,
                ReconciliationJobStatus.COMPLETED,
                total,
                matched,
                unmatched,
                duplicate,
                suspicious,
                LocalDateTime.now(businessClock)
        );
        requireSingleStateUpdate(updated, reconciliationJobId);
    }

    private int count(
            List<ReconciliationDecision> decisions,
            ReconciliationResultType type) {
        return (int) decisions.stream()
                .filter(decision -> decision.resultType() == type)
                .count();
    }

    private ReconciliationTransaction toModel(
            Transaction transaction) {
        return new ReconciliationTransaction(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getExternalTransactionNo(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getTransactionTime()
        );
    }

    private ReconciliationResult toEntity(
            Long reconciliationJobId,
            ReconciliationDecision decision) {
        ReconciliationResult result = new ReconciliationResult();
        result.setReconciliationJobId(reconciliationJobId);
        result.setCsvTransactionId(decision.csvTransactionId());
        result.setManualTransactionId(decision.manualTransactionId());
        result.setResultType(decision.resultType());
        result.setMatchMethod(decision.matchMethod());
        result.setReasonCode(decision.reasonCode());
        return result;
    }

    private void requireSingleStateUpdate(
            int updated,
            Long reconciliationJobId) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Reconciliation job state transition was rejected: "
                            + reconciliationJobId
            );
        }
    }

    private boolean markFailedIfNonTerminal(
            Long reconciliationJobId,
            String errorSummary) {
        if (reconciliationJobId == null || reconciliationJobId <= 0) {
            throw new IllegalArgumentException(
                    "reconciliationJobId must be a positive number"
            );
        }
        int updated = reconciliationJobMapper.failNonTerminal(
                reconciliationJobId,
                errorSummary,
                LocalDateTime.now(businessClock)
        );
        if (updated == 1) {
            return true;
        }
        ReconciliationJob current = reconciliationJobMapper.selectById(
                reconciliationJobId
        );
        if (current == null) {
            throw new ReconciliationJobNotFoundException(
                    reconciliationJobId
            );
        }
        if (isTerminal(current.getStatus())) {
            return false;
        }
        throw new IllegalStateException(
                "Reconciliation failure transition was rejected: "
                        + reconciliationJobId
        );
    }

    private boolean isTerminal(ReconciliationJobStatus status) {
        return status == ReconciliationJobStatus.COMPLETED
                || status == ReconciliationJobStatus.FAILED;
    }
}
