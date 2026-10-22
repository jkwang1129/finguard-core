package com.finguard.core.reconciliation.dto;
import com.finguard.core.reconciliation.model.ReconciliationJobStatus;
import jakarta.validation.constraints.*;
public record ReconciliationJobQueryRequest(@Min(1) Long page, @Min(1) @Max(100) Long size, ReconciliationJobStatus status, @Positive Long importJobId, @Positive Long createdBy) {
    public ReconciliationJobQueryRequest { page=page==null?1L:page; size=size==null?20L:size; }
}
