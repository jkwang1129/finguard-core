package com.finguard.core.importjob.model;

public enum ImportFileErrorCode {
    INVALID_UTF8,
    INVALID_HEADER,
    MALFORMED_CSV,
    NO_DATA_ROWS,
    TOO_MANY_ROWS,
    PROCESSING_FAILED
}
