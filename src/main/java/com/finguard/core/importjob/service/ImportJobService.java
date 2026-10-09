package com.finguard.core.importjob.service;

import com.finguard.core.importjob.dto.ImportJobQueryRequest;
import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.importjob.dto.ImportRowErrorQueryRequest;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.importjob.vo.ImportRowErrorResponse;

public interface ImportJobService {

    ImportJobResponse upload(
            String originalFileName,
            byte[] originalBytes,
            Long createdBy
    );

    ImportJobResponse getById(Long importJobId);

    PageResponse<ImportRowErrorResponse> queryErrors(
            Long importJobId,
            ImportRowErrorQueryRequest request
    );
    PageResponse<ImportJobResponse> query(ImportJobQueryRequest request);
}
