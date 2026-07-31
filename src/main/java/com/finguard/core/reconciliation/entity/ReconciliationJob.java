package com.finguard.core.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;

import java.time.LocalDateTime;

@TableName("reconciliation_jobs")
public class ReconciliationJob {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("import_job_id")
    private Long importJobId;

    private ReconciliationJobStatus status;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("matched_count")
    private Integer matchedCount;

    @TableField("unmatched_count")
    private Integer unmatchedCount;

    @TableField("duplicate_count")
    private Integer duplicateCount;

    @TableField("suspicious_count")
    private Integer suspiciousCount;

    @TableField("error_summary")
    private String errorSummary;

    @TableField("created_by")
    private Long createdBy;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

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

    public Long getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(Long importJobId) {
        this.importJobId = importJobId;
    }

    public ReconciliationJobStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationJobStatus status) {
        this.status = status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    public Integer getUnmatchedCount() {
        return unmatchedCount;
    }

    public void setUnmatchedCount(Integer unmatchedCount) {
        this.unmatchedCount = unmatchedCount;
    }

    public Integer getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public Integer getSuspiciousCount() {
        return suspiciousCount;
    }

    public void setSuspiciousCount(Integer suspiciousCount) {
        this.suspiciousCount = suspiciousCount;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
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
