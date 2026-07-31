package com.finguard.core.importjob.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ImportRowErrorQueryRequest(
        @Min(value = 1, message = "page must be at least 1")
        Long page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Long size) {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_SIZE = 20L;

    public ImportRowErrorQueryRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
}
