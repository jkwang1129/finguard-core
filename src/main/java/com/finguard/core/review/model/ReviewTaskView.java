package com.finguard.core.review.model;

import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;

import java.time.LocalDateTime;

public class ReviewTaskView {

    private Long id;
    private ReviewTaskSourceType sourceType;
    private Long reconciliationResultId;
    private Long riskHitId;
    private Long csvTransactionId;
    private ReconciliationResultType resultType;
    private RiskRuleCode ruleCode;
    private RiskReasonCode reasonCode;
    private ReviewTaskStatus status;
    private Integer version;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String decisionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReviewTaskSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ReviewTaskSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Long getReconciliationResultId() {
        return reconciliationResultId;
    }

    public void setReconciliationResultId(Long reconciliationResultId) {
        this.reconciliationResultId = reconciliationResultId;
    }

    public Long getRiskHitId() {
        return riskHitId;
    }

    public void setRiskHitId(Long riskHitId) {
        this.riskHitId = riskHitId;
    }

    public Long getCsvTransactionId() {
        return csvTransactionId;
    }

    public void setCsvTransactionId(Long csvTransactionId) {
        this.csvTransactionId = csvTransactionId;
    }

    public ReconciliationResultType getResultType() {
        return resultType;
    }

    public void setResultType(ReconciliationResultType resultType) {
        this.resultType = resultType;
    }

    public RiskRuleCode getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(RiskRuleCode ruleCode) {
        this.ruleCode = ruleCode;
    }

    public RiskReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(RiskReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public ReviewTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewTaskStatus status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
