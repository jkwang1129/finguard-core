package com.finguard.core.importjob.parser;

import java.util.List;
import java.util.Objects;

public record ParsedImportFile(
        String originalFileName,
        String fileHash,
        long fileSizeBytes,
        List<ParsedCsvRow> rows) {

    public ParsedImportFile {
        Objects.requireNonNull(
                originalFileName,
                "originalFileName must not be null"
        );
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        Objects.requireNonNull(rows, "rows must not be null");

        if (originalFileName.isBlank()) {
            throw new IllegalArgumentException(
                    "originalFileName must not be blank"
            );
        }
        if (!fileHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "fileHash must be a lowercase SHA-256 value"
            );
        }
        if (fileSizeBytes < 1) {
            throw new IllegalArgumentException(
                    "fileSizeBytes must be positive"
            );
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows must not be empty");
        }

        rows = List.copyOf(rows);
    }
}
