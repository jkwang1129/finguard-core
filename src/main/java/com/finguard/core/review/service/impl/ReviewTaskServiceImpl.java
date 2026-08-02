package com.finguard.core.review.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.dto.ReviewDecisionRequest;
import com.finguard.core.review.dto.ReviewTaskQueryRequest;
import com.finguard.core.review.entity.ReviewTask;
import com.finguard.core.review.exception.InvalidReviewOperationException;
import com.finguard.core.review.exception.InvalidReviewRequestException;
import com.finguard.core.review.exception.ReviewTaskNotFoundException;
import com.finguard.core.review.exception.ReviewVersionConflictException;
import com.finguard.core.review.mapper.ReviewTaskMapper;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.review.model.ReviewTaskView;
import com.finguard.core.review.service.ReviewTaskService;
import com.finguard.core.review.vo.ReviewTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class ReviewTaskServiceImpl implements ReviewTaskService {

    private static final int MAX_NOTE_CODE_POINTS = 255;

    private final ReviewTaskMapper reviewTaskMapper;
    private final Clock businessClock;

    public ReviewTaskServiceImpl(
            ReviewTaskMapper reviewTaskMapper,
            Clock businessClock) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.businessClock = businessClock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewTaskResponse> query(
            ReviewTaskQueryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ReviewTaskSourceType effectiveSource = effectiveSource(request);
        IPage<ReviewTaskView> page = reviewTaskMapper.selectTaskPage(
                new Page<>(request.page(), request.size()),
                request.status(),
                effectiveSource,
                request.resultType(),
                request.ruleCode()
        );
        List<ReviewTaskResponse> records = page.getRecords()
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

    @Override
    @Transactional(readOnly = true)
    public ReviewTaskResponse getById(Long reviewTaskId) {
        return toResponse(requireView(reviewTaskId));
    }

    @Override
    @Transactional
    public ReviewTaskResponse decide(
            Long reviewTaskId,
            ReviewDecisionRequest request,
            Long reviewerId) {
        requirePositive(reviewTaskId, "reviewTaskId");
        requirePositive(reviewerId, "reviewerId");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(
                request.decision(),
                "decision must not be null"
        );
        Objects.requireNonNull(
                request.version(),
                "version must not be null"
        );
        if (request.version() < 0) {
            throw new InvalidReviewRequestException(
                    "version must not be negative"
            );
        }

        ReviewTask current = reviewTaskMapper.selectById(reviewTaskId);
        if (current == null) {
            throw new ReviewTaskNotFoundException(reviewTaskId);
        }
        if (current.getStatus() != ReviewTaskStatus.PENDING) {
            throw new InvalidReviewOperationException(
                    "Review task is already completed: " + reviewTaskId
            );
        }
        if (!request.version().equals(current.getVersion())) {
            throw new ReviewVersionConflictException(reviewTaskId);
        }

        String decisionNote = normalizeNote(request.note());
        LocalDateTime reviewedAt = LocalDateTime.now(businessClock)
                .truncatedTo(ChronoUnit.MILLIS);
        int updated = reviewTaskMapper.updateDecision(
                reviewTaskId,
                request.decision().toStatus(),
                request.version(),
                reviewerId,
                reviewedAt,
                decisionNote
        );
        if (updated != 1) {
            throw new ReviewVersionConflictException(reviewTaskId);
        }
        return toResponse(requireView(reviewTaskId));
    }

    private ReviewTaskSourceType effectiveSource(
            ReviewTaskQueryRequest request) {
        if (request.resultType() != null
                && request.ruleCode() != null) {
            throw new InvalidReviewRequestException(
                    "resultType and ruleCode cannot be combined"
            );
        }
        if (request.resultType() == ReconciliationResultType.MATCHED) {
            throw new InvalidReviewRequestException(
                    "resultType must identify a reconciliation exception"
            );
        }
        ReviewTaskSourceType source = request.sourceType();
        if (request.resultType() != null) {
            if (source == ReviewTaskSourceType.RISK_HIT) {
                throw new InvalidReviewRequestException(
                        "resultType is only valid for exception tasks"
                );
            }
            source = ReviewTaskSourceType.RECONCILIATION_EXCEPTION;
        }
        if (request.ruleCode() != null) {
            if (source
                    == ReviewTaskSourceType.RECONCILIATION_EXCEPTION) {
                throw new InvalidReviewRequestException(
                        "ruleCode is only valid for risk tasks"
                );
            }
            source = ReviewTaskSourceType.RISK_HIT;
        }
        return source;
    }

    private ReviewTaskView requireView(Long reviewTaskId) {
        requirePositive(reviewTaskId, "reviewTaskId");
        ReviewTaskView view = reviewTaskMapper.selectTaskViewById(
                reviewTaskId
        );
        if (view == null) {
            throw new ReviewTaskNotFoundException(reviewTaskId);
        }
        return view;
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        int codePoints = normalized.codePointCount(
                0,
                normalized.length()
        );
        if (codePoints > MAX_NOTE_CODE_POINTS) {
            throw new InvalidReviewRequestException(
                    "note must not exceed 255 characters"
            );
        }
        return normalized;
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    field + " must be a positive id"
            );
        }
    }

    private ReviewTaskResponse toResponse(ReviewTaskView view) {
        return new ReviewTaskResponse(
                view.getId(),
                view.getSourceType(),
                view.getReconciliationResultId(),
                view.getRiskHitId(),
                view.getCsvTransactionId(),
                view.getResultType(),
                view.getRuleCode(),
                view.getReasonCode(),
                view.getStatus(),
                view.getVersion(),
                view.getReviewedBy(),
                view.getReviewedAt(),
                view.getDecisionNote(),
                view.getCreatedAt(),
                view.getUpdatedAt()
        );
    }
}
