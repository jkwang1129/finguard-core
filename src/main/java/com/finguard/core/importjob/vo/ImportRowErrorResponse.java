package com.finguard.core.importjob.vo;

import com.finguard.core.importjob.model.ImportRowErrorCode;

import java.time.LocalDateTime;

public record ImportRowErrorResponse(
        Long id,
        Long importJobId,
        Integer rowNumber,
        String field,
        ImportRowErrorCode errorCode,
        String rejectedValue,
        String message,
        LocalDateTime createdAt) {
}
