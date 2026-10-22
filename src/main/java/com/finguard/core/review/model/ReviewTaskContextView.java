package com.finguard.core.review.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReviewTaskContextView {

    private Long reviewTaskId;
    private Integer reviewTaskVersion;
    private String reviewTaskStatus;
    private String ruleCode;
    private String taskReasonCode;
    private LocalDateTime reviewTaskCreatedAt;
    private Long transactionId;
    private Long accountId;
    private BigDecimal transactionAmount;
    private String currency;
    private LocalDateTime occurredAt;
    private String transactionSourceType;
    private String reconciliationResultType;
    private String matchMethod;
    private String reconciliationReasonCode;
    private BigDecimal observedAmount;
    private BigDecimal thresholdAmount;
    private Integer observedCount;
    private Integer thresholdCount;
    private Integer windowSeconds;
    private String accountStatus;
    private String accountDisplayName;

    public Long getReviewTaskId() {
        return reviewTaskId;
    }

    public void setReviewTaskId(Long reviewTaskId) {
        this.reviewTaskId = reviewTaskId;
    }

    public Integer getReviewTaskVersion() {
        return reviewTaskVersion;
    }

    public void setReviewTaskVersion(Integer reviewTaskVersion) {
        this.reviewTaskVersion = reviewTaskVersion;
    }

    public String getReviewTaskStatus() {
        return reviewTaskStatus;
    }

    public void setReviewTaskStatus(String reviewTaskStatus) {
        this.reviewTaskStatus = reviewTaskStatus;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getTaskReasonCode() {
        return taskReasonCode;
    }

    public void setTaskReasonCode(String taskReasonCode) {
        this.taskReasonCode = taskReasonCode;
    }

    public LocalDateTime getReviewTaskCreatedAt() {
        return reviewTaskCreatedAt;
    }

    public void setReviewTaskCreatedAt(
            LocalDateTime reviewTaskCreatedAt) {
        this.reviewTaskCreatedAt = reviewTaskCreatedAt;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getTransactionSourceType() {
        return transactionSourceType;
    }

    public void setTransactionSourceType(String transactionSourceType) {
        this.transactionSourceType = transactionSourceType;
    }

    public String getReconciliationResultType() {
        return reconciliationResultType;
    }

    public void setReconciliationResultType(
            String reconciliationResultType) {
        this.reconciliationResultType = reconciliationResultType;
    }

    public String getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(String matchMethod) {
        this.matchMethod = matchMethod;
    }

    public String getReconciliationReasonCode() {
        return reconciliationReasonCode;
    }

    public void setReconciliationReasonCode(
            String reconciliationReasonCode) {
        this.reconciliationReasonCode = reconciliationReasonCode;
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

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getAccountDisplayName() {
        return accountDisplayName;
    }

    public void setAccountDisplayName(String accountDisplayName) {
        this.accountDisplayName = accountDisplayName;
    }
}
