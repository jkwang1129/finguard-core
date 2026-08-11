package com.finguard.core.observability;

import com.finguard.core.importjob.model.ImportJobStatus;

public record ImportJobTerminalEvent(ImportJobStatus outcome) {
}
