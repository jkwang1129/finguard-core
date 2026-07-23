package com.finguard.core.common.vo;

import java.util.List;
import java.util.Objects;

public record PageResponse<T>(
        long page,
        long size,
        long total,
        long pages,
        List<T> records
) {

    public PageResponse {
        Objects.requireNonNull(records, "records must not be null");
        records = List.copyOf(records);
    }
}
