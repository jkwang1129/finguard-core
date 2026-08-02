package com.finguard.core.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;

import java.time.LocalDateTime;

@TableName("review_tasks")
public class ReviewTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("source_type")
    private ReviewTaskSourceType sourceType;

    @TableField("reconciliation_result_id")
    private Long reconciliationResultId;

    @TableField("risk_hit_id")
    private Long riskHitId;

    @TableField("status")
    private ReviewTaskStatus status;

    @TableField("version")
    private Integer version;

    @TableField("reviewed_by")
    private Long reviewedBy;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("decision_note")
    private String decisionNote;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
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
