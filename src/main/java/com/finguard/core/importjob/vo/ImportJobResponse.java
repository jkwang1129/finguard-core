package com.finguard.core.importjob.vo;

import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportJobStatus;

import java.time.LocalDateTime;

public record ImportJobResponse(
        Long id,
        String originalFileName,
        String fileHash,
        Long fileSizeBytes,
        ImportJobStatus status,
        Integer totalRows,
        Integer successRows,
        Integer failedRows,
        Integer duplicateRows,
        ImportFileErrorCode fileErrorCode,
        String errorSummary,
        Long createdBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean duplicateFile) {
}
