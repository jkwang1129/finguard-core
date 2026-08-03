package com.finguard.core.audit.vo;

import com.finguard.core.audit.model.AuditActionCode;
import com.finguard.core.audit.model.AuditActorType;
import com.finguard.core.audit.model.AuditOutcome;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        AuditActionCode actionCode,
        AuditActorType actorType,
        Long actorUserId,
        Long initiatedBy,
        AuditOutcome outcome,
        Long importJobId,
        Long reconciliationJobId,
        Long reviewTaskId,
        String summary,
        LocalDateTime createdAt
) {
}
