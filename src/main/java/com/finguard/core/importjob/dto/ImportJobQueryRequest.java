package com.finguard.core.importjob.dto;
import com.finguard.core.importjob.model.ImportJobStatus;
import jakarta.validation.constraints.*;
public record ImportJobQueryRequest(@Min(1) Long page, @Min(1) @Max(100) Long size, ImportJobStatus status, @Positive Long createdBy) {
    public ImportJobQueryRequest { page=page==null?1L:page; size=size==null?20L:size; }
}
