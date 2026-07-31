package com.finguard.core.importjob.parser;

import java.util.Objects;

public record PreparedImportFile(
        String originalFileName,
        byte[] originalBytes,
        String fileHash,
        long fileSizeBytes) {

    public PreparedImportFile {
        Objects.requireNonNull(
                originalFileName,
                "originalFileName must not be null"
        );
        Objects.requireNonNull(
                originalBytes,
                "originalBytes must not be null"
        );
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        originalBytes = originalBytes.clone();
        if (fileSizeBytes != originalBytes.length) {
            throw new IllegalArgumentException(
                    "fileSizeBytes must match originalBytes length"
            );
        }
    }

    @Override
    public byte[] originalBytes() {
        return originalBytes.clone();
    }
}
