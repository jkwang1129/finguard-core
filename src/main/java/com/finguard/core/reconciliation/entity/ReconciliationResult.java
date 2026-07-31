package com.finguard.core.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finguard.core.reconciliation.model.ReconciliationMatchMethod;
import com.finguard.core.reconciliation.model.ReconciliationReasonCode;
import com.finguard.core.reconciliation.model.ReconciliationResultType;

import java.time.LocalDateTime;

@TableName("reconciliation_results")
public class ReconciliationResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("reconciliation_job_id")
    private Long reconciliationJobId;

    @TableField("csv_transaction_id")
    private Long csvTransactionId;

    @TableField("manual_transaction_id")
    private Long manualTransactionId;

    @TableField("result_type")
    private ReconciliationResultType resultType;

    @TableField("match_method")
    private ReconciliationMatchMethod matchMethod;

    @TableField("reason_code")
    private ReconciliationReasonCode reasonCode;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReconciliationJobId() {
        return reconciliationJobId;
    }

    public void setReconciliationJobId(Long reconciliationJobId) {
        this.reconciliationJobId = reconciliationJobId;
    }

    public Long getCsvTransactionId() {
        return csvTransactionId;
    }

    public void setCsvTransactionId(Long csvTransactionId) {
        this.csvTransactionId = csvTransactionId;
    }

    public Long getManualTransactionId() {
        return manualTransactionId;
    }

    public void setManualTransactionId(Long manualTransactionId) {
        this.manualTransactionId = manualTransactionId;
    }

    public ReconciliationResultType getResultType() {
        return resultType;
    }

    public void setResultType(ReconciliationResultType resultType) {
        this.resultType = resultType;
    }

    public ReconciliationMatchMethod getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(ReconciliationMatchMethod matchMethod) {
        this.matchMethod = matchMethod;
    }

    public ReconciliationReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(ReconciliationReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
