package com.finguard.core.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finguard.core.risk.model.RiskReasonCode;
import com.finguard.core.risk.model.RiskRuleCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("risk_hits")
public class RiskHit {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("reconciliation_result_id")
    private Long reconciliationResultId;

    @TableField("rule_code")
    private RiskRuleCode ruleCode;

    @TableField("reason_code")
    private RiskReasonCode reasonCode;

    @TableField("observed_amount")
    private BigDecimal observedAmount;

    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    @TableField("observed_count")
    private Integer observedCount;

    @TableField("threshold_count")
    private Integer thresholdCount;

    @TableField("window_seconds")
    private Integer windowSeconds;

    @TableField("reason_summary")
    private String reasonSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReconciliationResultId() {
        return reconciliationResultId;
    }

    public void setReconciliationResultId(Long reconciliationResultId) {
        this.reconciliationResultId = reconciliationResultId;
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

    public BigDecimal getObservedAmount() {
        return observedAmount;
    }

    public void setObservedAmount(BigDecimal observedAmount) {
        this.observedAmount = observedAmount;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public Integer getObservedCount() {
        return observedCount;
    }

    public void setObservedCount(Integer observedCount) {
        this.observedCount = observedCount;
    }

    public Integer getThresholdCount() {
        return thresholdCount;
    }

    public void setThresholdCount(Integer thresholdCount) {
        this.thresholdCount = thresholdCount;
    }

    public Integer getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(Integer windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public String getReasonSummary() {
        return reasonSummary;
    }

    public void setReasonSummary(String reasonSummary) {
        this.reasonSummary = reasonSummary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
