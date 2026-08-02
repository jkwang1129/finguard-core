package com.finguard.core.risk.service;

import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.model.ReconciliationTransaction;
import com.finguard.core.risk.config.RiskProperties;
import com.finguard.core.risk.entity.RiskHit;
import com.finguard.core.risk.mapper.RiskHitMapper;
import com.finguard.core.risk.rule.RiskEvaluationContext;
import com.finguard.core.risk.rule.RiskRule;
import com.finguard.core.risk.rule.RiskRuleResult;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RiskEvaluationService {

    static final int BATCH_SIZE = 500;

    private final TransactionMapper transactionMapper;
    private final RiskHitMapper riskHitMapper;
    private final List<RiskRule> rules;
    private final RiskProperties properties;
    private final Clock businessClock;

    public RiskEvaluationService(
            TransactionMapper transactionMapper,
            RiskHitMapper riskHitMapper,
            List<RiskRule> rules,
            RiskProperties properties,
            Clock businessClock) {
        this.transactionMapper = transactionMapper;
        this.riskHitMapper = riskHitMapper;
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(RiskRule::getOrder))
                .toList();
        this.properties = properties;
        this.businessClock = businessClock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<RiskHit> evaluateAndPersist(
            Long reconciliationJobId,
            List<ReconciliationResult> results,
            List<ReconciliationTransaction> currentTransactions) {
        validateInputs(
                reconciliationJobId,
                results,
                currentTransactions
        );
        Map<Long, ReconciliationTransaction> currentById =
                currentTransactions.stream().collect(Collectors.toMap(
                        ReconciliationTransaction::id,
                        Function.identity()
                ));
        Map<Long, List<ReconciliationTransaction>> historyByAccount =
                loadHistoryByAccount(currentTransactions);
        List<RiskHit> candidates = new ArrayList<>();
        for (ReconciliationResult result : results) {
            ReconciliationTransaction current = currentById.get(
                    result.getCsvTransactionId()
            );
            if (current == null) {
                throw new IllegalStateException(
                        "Reconciliation result references unknown CSV transaction"
                );
            }
            RiskEvaluationContext context = new RiskEvaluationContext(
                    reconciliationJobId,
                    result.getId(),
                    current,
                    result.getResultType(),
                    result.getReasonCode(),
                    historyByAccount.getOrDefault(
                            current.accountId(),
                            List.of(current)
                    ),
                    businessClock.getZone()
            );
            for (RiskRule rule : rules) {
                if (!rule.enabled()) {
                    continue;
                }
                rule.evaluate(context)
                        .map(ruleResult -> toEntity(
                                result.getId(),
                                ruleResult
                        ))
                        .ifPresent(candidates::add);
            }
        }
        persistCandidates(candidates);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> resultIds = results.stream()
                .map(ReconciliationResult::getId)
                .toList();
        List<RiskHit> persisted = new ArrayList<>();
        for (int offset = 0;
             offset < resultIds.size();
             offset += BATCH_SIZE) {
            persisted.addAll(riskHitMapper.selectByReconciliationResultIds(
                    resultIds.subList(
                            offset,
                            Math.min(offset + BATCH_SIZE, resultIds.size())
                    )
            ));
        }
        if (persisted.size() != candidates.size()) {
            throw new IllegalStateException(
                    "Persisted risk hit count was inconsistent"
            );
        }
        return List.copyOf(persisted);
    }

    private void validateInputs(
            Long reconciliationJobId,
            List<ReconciliationResult> results,
            List<ReconciliationTransaction> currentTransactions) {
        if (reconciliationJobId == null || reconciliationJobId <= 0) {
            throw new IllegalArgumentException(
                    "reconciliationJobId must be positive"
            );
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("results must not be empty");
        }
        if (currentTransactions == null
                || currentTransactions.isEmpty()) {
            throw new IllegalArgumentException(
                    "currentTransactions must not be empty"
            );
        }
        if (results.stream().anyMatch(result -> result.getId() == null
                || result.getId() <= 0
                || !reconciliationJobId.equals(
                result.getReconciliationJobId()))) {
            throw new IllegalArgumentException(
                    "results must be persisted for reconciliation job"
            );
        }
        long distinctResultTransactions = results.stream()
                .map(ReconciliationResult::getCsvTransactionId)
                .distinct()
                .count();
        long distinctCurrentTransactions = currentTransactions.stream()
                .map(ReconciliationTransaction::id)
                .distinct()
                .count();
        if (results.size() != currentTransactions.size()
                || distinctResultTransactions != results.size()
                || distinctCurrentTransactions != currentTransactions.size()) {
            throw new IllegalArgumentException(
                    "results and current transactions must be one-to-one"
            );
        }
    }

    private Map<Long, List<ReconciliationTransaction>> loadHistoryByAccount(
            List<ReconciliationTransaction> currentTransactions) {
        Duration maxWindow = maxEnabledWindow();
        if (maxWindow.isZero()) {
            return currentTransactions.stream().collect(Collectors.groupingBy(
                    ReconciliationTransaction::accountId,
                    LinkedHashMap::new,
                    Collectors.toUnmodifiableList()
            ));
        }
        LocalDateTime fromTime = currentTransactions.stream()
                .map(ReconciliationTransaction::transactionTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow()
                .minus(maxWindow);
        LocalDateTime toTime = currentTransactions.stream()
                .map(ReconciliationTransaction::transactionTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();
        List<Long> accountIds = currentTransactions.stream()
                .map(ReconciliationTransaction::accountId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, ReconciliationTransaction> historyById = new HashMap<>();
        for (int offset = 0;
             offset < accountIds.size();
             offset += BATCH_SIZE) {
            transactionMapper.selectCsvByAccountsAndTime(
                    accountIds.subList(
                            offset,
                            Math.min(offset + BATCH_SIZE, accountIds.size())
                    ),
                    fromTime,
                    toTime
            ).stream().map(this::toModel).forEach(transaction ->
                    historyById.put(transaction.id(), transaction)
            );
        }
        currentTransactions.forEach(transaction ->
                historyById.putIfAbsent(transaction.id(), transaction)
        );
        return historyById.values().stream()
                .sorted(Comparator
                        .comparing(ReconciliationTransaction::accountId)
                        .thenComparing(
                                ReconciliationTransaction::transactionTime
                        )
                        .thenComparing(ReconciliationTransaction::id))
                .collect(Collectors.groupingBy(
                        ReconciliationTransaction::accountId,
                        LinkedHashMap::new,
                        Collectors.toUnmodifiableList()
                ));
    }

    private Duration maxEnabledWindow() {
        Duration duplicate = properties.getPossibleDuplicate().isEnabled()
                ? properties.getPossibleDuplicate().getWindow()
                : Duration.ZERO;
        Duration frequent = properties.getFrequentTransaction().isEnabled()
                ? properties.getFrequentTransaction().getWindow()
                : Duration.ZERO;
        return duplicate.compareTo(frequent) >= 0 ? duplicate : frequent;
    }

    private void persistCandidates(List<RiskHit> candidates) {
        for (int offset = 0;
             offset < candidates.size();
             offset += BATCH_SIZE) {
            List<RiskHit> batch = candidates.subList(
                    offset,
                    Math.min(offset + BATCH_SIZE, candidates.size())
            );
            int inserted = riskHitMapper.insertBatch(batch);
            if (inserted != batch.size()) {
                throw new IllegalStateException(
                        "Risk hit batch count was inconsistent"
                );
            }
        }
    }

    private RiskHit toEntity(
            Long reconciliationResultId,
            RiskRuleResult result) {
        RiskHit hit = new RiskHit();
        hit.setReconciliationResultId(reconciliationResultId);
        hit.setRuleCode(result.ruleCode());
        hit.setReasonCode(result.reasonCode());
        hit.setObservedAmount(result.observedAmount());
        hit.setThresholdAmount(result.thresholdAmount());
        hit.setObservedCount(result.observedCount());
        hit.setThresholdCount(result.thresholdCount());
        hit.setWindowSeconds(result.windowSeconds());
        hit.setReasonSummary(result.reasonSummary());
        return hit;
    }

    private ReconciliationTransaction toModel(Transaction transaction) {
        return new ReconciliationTransaction(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getExternalTransactionNo(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getTransactionTime()
        );
    }
}
