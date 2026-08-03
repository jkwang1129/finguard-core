package com.finguard.core.audit.entity;

import com.finguard.core.audit.model.AuditActionCode;
import com.finguard.core.audit.model.AuditActorType;
import com.finguard.core.audit.model.AuditOutcome;

import java.time.LocalDateTime;

public class AuditLog {

    private Long id;
    private AuditActionCode actionCode;
    private AuditActorType actorType;
    private Long actorUserId;
    private Long initiatedBy;
    private AuditOutcome outcome;
    private Long importJobId;
    private Long reconciliationJobId;
    private Long reviewTaskId;
    private String summary;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuditActionCode getActionCode() {
        return actionCode;
    }

    public void setActionCode(AuditActionCode actionCode) {
        this.actionCode = actionCode;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public void setActorType(AuditActorType actorType) {
        this.actorType = actorType;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public Long getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(Long initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AuditOutcome outcome) {
        this.outcome = outcome;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(Long importJobId) {
        this.importJobId = importJobId;
    }

    public Long getReconciliationJobId() {
        return reconciliationJobId;
    }

    public void setReconciliationJobId(Long reconciliationJobId) {
        this.reconciliationJobId = reconciliationJobId;
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
    }

    public void setReviewTaskId(Long reviewTaskId) {
        this.reviewTaskId = reviewTaskId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
