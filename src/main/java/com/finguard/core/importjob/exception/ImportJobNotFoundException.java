package com.finguard.core.importjob.exception;

public class ImportJobNotFoundException extends RuntimeException {

    public ImportJobNotFoundException(Long importJobId) {
        super("Import job was not found: " + importJobId);
    }
}
