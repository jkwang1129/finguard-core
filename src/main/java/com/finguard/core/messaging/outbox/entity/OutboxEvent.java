package com.finguard.core.messaging.outbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finguard.core.messaging.outbox.model.OutboxEventType;
import com.finguard.core.messaging.outbox.model.OutboxStatus;

import java.time.LocalDateTime;

@TableName("outbox_events")
public class OutboxEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("event_type")
    private OutboxEventType eventType;
    @TableField("aggregate_id")
    private Long aggregateId;
    @TableField("schema_version")
    private Integer schemaVersion;
    private OutboxStatus status;
    private Integer attempts;
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;
    @TableField("last_error_summary")
    private String lastErrorSummary;
    @TableField("sent_at")
    private LocalDateTime sentAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OutboxEventType getEventType() { return eventType; }
    public void setEventType(OutboxEventType eventType) { this.eventType = eventType; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastErrorSummary() { return lastErrorSummary; }
    public void setLastErrorSummary(String lastErrorSummary) { this.lastErrorSummary = lastErrorSummary; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
