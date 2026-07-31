package com.finguard.core.importjob.parser;

import java.util.List;
import java.util.Objects;

public record ParsedCsvRow(
        int rowNumber,
        List<String> values) {

    public ParsedCsvRow {
        if (rowNumber < 2) {
            throw new IllegalArgumentException(
                    "rowNumber must identify a data record"
            );
        }
        Objects.requireNonNull(values, "values must not be null");
        values = List.copyOf(values);
    }
}
