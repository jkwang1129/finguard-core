package com.finguard.core.audit.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.audit.dto.AuditLogQueryRequest;
import com.finguard.core.audit.entity.AuditLog;
import com.finguard.core.audit.mapper.AuditLogMapper;
import com.finguard.core.audit.model.AuditActionCode;
import com.finguard.core.audit.model.AuditActorType;
import com.finguard.core.audit.model.AuditOutcome;
import com.finguard.core.audit.model.ReconciliationAuditCounts;
import com.finguard.core.audit.service.AuditLogService;
import com.finguard.core.audit.vo.AuditLogResponse;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.review.model.ReviewDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final String CSV_UPLOAD_ACCEPTED_SUMMARY =
            "CSV upload accepted";
    private static final String IMPORT_FAILED_SUMMARY = "Import failed";
    private static final String REVIEW_CONFIRMED_SUMMARY =
            "Review task confirmed";
    private static final String REVIEW_IGNORED_SUMMARY =
            "Review task ignored";

    private final AuditLogMapper auditLogMapper;
    private final Clock businessClock;

    public AuditLogServiceImpl(
            AuditLogMapper auditLogMapper,
            Clock businessClock) {
        this.auditLogMapper = auditLogMapper;
        this.businessClock = businessClock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCsvUploadAccepted(
            Long importJobId,
            Long initiatedBy) {
        insert(userEvent(
                AuditActionCode.CSV_UPLOAD_ACCEPTED,
                AuditOutcome.SUCCESS,
                initiatedBy,
                importJobId,
                null,
                null,
                CSV_UPLOAD_ACCEPTED_SUMMARY
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordImportFailed(Long importJobId, Long initiatedBy) {
        insert(systemEvent(
                AuditActionCode.IMPORT_FAILED,
                AuditOutcome.FAILED,
                initiatedBy,
                importJobId,
                null,
                null,
                IMPORT_FAILED_SUMMARY
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReconciliationCompleted(
            Long reconciliationJobId,
            Long initiatedBy,
            ReconciliationAuditCounts counts) {
        Objects.requireNonNull(counts, "counts must not be null");
        String summary = "Reconciliation completed: total="
                + counts.total()
                + ", matched=" + counts.matched()
                + ", unmatched=" + counts.unmatched()
                + ", duplicate=" + counts.duplicate()
                + ", suspicious=" + counts.suspicious();
        insert(systemEvent(
                AuditActionCode.RECONCILIATION_COMPLETED,
                AuditOutcome.SUCCESS,
                initiatedBy,
                null,
                reconciliationJobId,
                null,
                summary
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReviewDecision(
            Long reviewTaskId,
            Long reviewerId,
            ReviewDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        AuditActionCode actionCode;
        String summary;
        switch (decision) {
            case CONFIRMED -> {
                actionCode = AuditActionCode.REVIEW_CONFIRMED;
                summary = REVIEW_CONFIRMED_SUMMARY;
            }
            case IGNORED -> {
                actionCode = AuditActionCode.REVIEW_IGNORED;
                summary = REVIEW_IGNORED_SUMMARY;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported review decision: " + decision
            );
        }
        insert(userEvent(
                actionCode,
                AuditOutcome.SUCCESS,
                reviewerId,
                null,
                null,
                reviewTaskId,
                summary
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> query(
            AuditLogQueryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        IPage<AuditLog> page = auditLogMapper.selectPage(
                new Page<>(request.page(), request.size()),
                request.actionCode(),
                request.initiatedBy()
        );
        List<AuditLogResponse> records = page.getRecords().stream()
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

    private AuditLog userEvent(
            AuditActionCode actionCode,
            AuditOutcome outcome,
            Long userId,
            Long importJobId,
            Long reconciliationJobId,
            Long reviewTaskId,
            String summary) {
        requirePositive(userId, "userId");
        AuditLog auditLog = baseEvent(
                actionCode,
                AuditActorType.USER,
                userId,
                outcome,
                importJobId,
                reconciliationJobId,
                reviewTaskId,
                summary
        );
        auditLog.setActorUserId(userId);
        return auditLog;
    }

    private AuditLog systemEvent(
            AuditActionCode actionCode,
            AuditOutcome outcome,
            Long initiatedBy,
            Long importJobId,
            Long reconciliationJobId,
            Long reviewTaskId,
            String summary) {
        return baseEvent(
                actionCode,
                AuditActorType.SYSTEM,
                initiatedBy,
                outcome,
                importJobId,
                reconciliationJobId,
                reviewTaskId,
                summary
        );
    }

    private AuditLog baseEvent(
            AuditActionCode actionCode,
            AuditActorType actorType,
            Long initiatedBy,
            AuditOutcome outcome,
            Long importJobId,
            Long reconciliationJobId,
            Long reviewTaskId,
            String summary) {
        Objects.requireNonNull(actionCode, "actionCode must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        requirePositive(initiatedBy, "initiatedBy");
        requirePositiveTarget(
                importJobId,
                reconciliationJobId,
                reviewTaskId
        );
        AuditLog auditLog = new AuditLog();
        auditLog.setActionCode(actionCode);
        auditLog.setActorType(actorType);
        auditLog.setInitiatedBy(initiatedBy);
        auditLog.setOutcome(outcome);
        auditLog.setImportJobId(importJobId);
        auditLog.setReconciliationJobId(reconciliationJobId);
        auditLog.setReviewTaskId(reviewTaskId);
        auditLog.setSummary(summary);
        auditLog.setCreatedAt(LocalDateTime.now(businessClock)
                .truncatedTo(ChronoUnit.MILLIS));
        return auditLog;
    }

    private void insert(AuditLog auditLog) {
        int inserted = auditLogMapper.insert(auditLog);
        if (inserted != 1 || auditLog.getId() == null) {
            throw new IllegalStateException(
                    "Audit log could not be created"
            );
        }
    }

    private void requirePositiveTarget(
            Long importJobId,
            Long reconciliationJobId,
            Long reviewTaskId) {
        int present = 0;
        present += importJobId == null ? 0 : 1;
        present += reconciliationJobId == null ? 0 : 1;
        present += reviewTaskId == null ? 0 : 1;
        if (present != 1) {
            throw new IllegalArgumentException(
                    "Exactly one audit target must be provided"
            );
        }
        if (importJobId != null) {
            requirePositive(importJobId, "importJobId");
        }
        if (reconciliationJobId != null) {
            requirePositive(reconciliationJobId, "reconciliationJobId");
        }
        if (reviewTaskId != null) {
            requirePositive(reviewTaskId, "reviewTaskId");
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    field + " must be a positive id"
            );
        }
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActionCode(),
                auditLog.getActorType(),
                auditLog.getActorUserId(),
                auditLog.getInitiatedBy(),
                auditLog.getOutcome(),
                auditLog.getImportJobId(),
                auditLog.getReconciliationJobId(),
                auditLog.getReviewTaskId(),
                auditLog.getSummary(),
                auditLog.getCreatedAt()
        );
    }
}
