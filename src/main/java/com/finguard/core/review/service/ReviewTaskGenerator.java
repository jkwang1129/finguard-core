package com.finguard.core.review.service;

import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.entity.ReviewTask;
import com.finguard.core.review.mapper.ReviewTaskMapper;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.risk.entity.RiskHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ReviewTaskGenerator {

    static final int BATCH_SIZE = 500;

    private final ReviewTaskMapper reviewTaskMapper;

    public ReviewTaskGenerator(ReviewTaskMapper reviewTaskMapper) {
        this.reviewTaskMapper = reviewTaskMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReviewTask> generateAndPersist(
            List<ReconciliationResult> results,
            List<RiskHit> riskHits) {
        if (results == null || riskHits == null) {
            throw new IllegalArgumentException(
                    "results and riskHits must not be null"
            );
        }
        List<ReviewTask> candidates = new ArrayList<>();
        results.stream()
                .filter(result -> requiresExceptionReview(
                        result.getResultType()
                ))
                .sorted(Comparator.comparing(ReconciliationResult::getId))
                .map(this::forReconciliationException)
                .forEach(candidates::add);
        riskHits.stream()
                .sorted(Comparator.comparing(RiskHit::getId))
                .map(this::forRiskHit)
                .forEach(candidates::add);
        persist(candidates);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ReviewTask> persisted = new ArrayList<>();
        List<Long> resultIds = candidates.stream()
                .map(ReviewTask::getReconciliationResultId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Long> riskHitIds = candidates.stream()
                .map(ReviewTask::getRiskHitId)
                .filter(java.util.Objects::nonNull)
                .toList();
        selectByResultIds(resultIds, persisted);
        selectByRiskHitIds(riskHitIds, persisted);
        if (persisted.size() != candidates.size()) {
            throw new IllegalStateException(
                    "Persisted review task count was inconsistent"
            );
        }
        return persisted.stream()
                .sorted(Comparator.comparing(ReviewTask::getId))
                .toList();
    }

    private boolean requiresExceptionReview(
            ReconciliationResultType resultType) {
        return resultType == ReconciliationResultType.UNMATCHED
                || resultType == ReconciliationResultType.DUPLICATE
                || resultType == ReconciliationResultType.SUSPICIOUS;
    }

    private ReviewTask forReconciliationException(
            ReconciliationResult result) {
        if (result.getId() == null || result.getId() <= 0) {
            throw new IllegalArgumentException(
                    "reconciliation result must be persisted"
            );
        }
        ReviewTask task = pendingTask();
        task.setSourceType(
                ReviewTaskSourceType.RECONCILIATION_EXCEPTION
        );
        task.setReconciliationResultId(result.getId());
        return task;
    }

    private ReviewTask forRiskHit(RiskHit riskHit) {
        if (riskHit.getId() == null || riskHit.getId() <= 0) {
            throw new IllegalArgumentException(
                    "risk hit must be persisted"
            );
        }
        ReviewTask task = pendingTask();
        task.setSourceType(ReviewTaskSourceType.RISK_HIT);
        task.setRiskHitId(riskHit.getId());
        return task;
    }

    private ReviewTask pendingTask() {
        ReviewTask task = new ReviewTask();
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setVersion(0);
        return task;
    }

    private void persist(List<ReviewTask> candidates) {
        for (int offset = 0;
             offset < candidates.size();
             offset += BATCH_SIZE) {
            List<ReviewTask> batch = candidates.subList(
                    offset,
                    Math.min(offset + BATCH_SIZE, candidates.size())
            );
            int inserted = reviewTaskMapper.insertBatch(batch);
            if (inserted != batch.size()) {
                throw new IllegalStateException(
                        "Review task batch count was inconsistent"
                );
            }
        }
    }

    private void selectByResultIds(
            List<Long> ids,
            List<ReviewTask> target) {
        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            target.addAll(reviewTaskMapper.selectByReconciliationResultIds(
                    ids.subList(
                            offset,
                            Math.min(offset + BATCH_SIZE, ids.size())
                    )
            ));
        }
    }

    private void selectByRiskHitIds(
            List<Long> ids,
            List<ReviewTask> target) {
        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            target.addAll(reviewTaskMapper.selectByRiskHitIds(
                    ids.subList(
                            offset,
                            Math.min(offset + BATCH_SIZE, ids.size())
                    )
            ));
        }
    }
}
