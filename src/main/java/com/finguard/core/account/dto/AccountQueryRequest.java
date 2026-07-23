package com.finguard.core.account.dto;

import com.finguard.core.account.model.AccountStatus;
import com.finguard.core.account.model.AccountType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AccountQueryRequest(
        @Min(value = 1, message = "page must be at least 1")
        Long page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Long size,

        AccountStatus status,

        AccountType accountType,

        @Size(max = 100, message = "keyword must not exceed 100 characters")
        String keyword
) {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_SIZE = 20L;

    public AccountQueryRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
}
