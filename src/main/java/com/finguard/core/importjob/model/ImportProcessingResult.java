package com.finguard.core.importjob.model;

/**
 * Describes how an asynchronous import delivery was resolved.
 */
public enum ImportProcessingResult {
    PROCESSED,
    RECOVERED,
    ALREADY_COMPLETED
}
