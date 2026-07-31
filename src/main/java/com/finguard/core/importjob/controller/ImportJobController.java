package com.finguard.core.importjob.controller;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.importjob.dto.ImportRowErrorQueryRequest;
import com.finguard.core.importjob.exception.InvalidImportFileRequestException;
import com.finguard.core.importjob.model.ImportFileRequestErrorCode;
import com.finguard.core.importjob.service.ImportJobService;
import com.finguard.core.importjob.vo.ImportJobResponse;
import com.finguard.core.importjob.vo.ImportRowErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/import-jobs")
public class ImportJobController {

    private final ImportJobService importJobService;

    public ImportJobController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobResponse> upload(
            @RequestPart(value = "file", required = false)
            MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        if (file == null) {
            throw new InvalidImportFileRequestException(
                    ImportFileRequestErrorCode.MISSING_FILE,
                    "CSV file is required"
            );
        }
        ImportJobResponse response = importJobService.upload(
                safeFileName(file.getOriginalFilename()),
                file.getBytes(),
                authenticatedUserId(jwt)
        );
        if (response.duplicateFile()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.LOCATION,
                        URI.create(
                                "/api/import-jobs/" + response.id()
                        ).toString()
                )
                .body(response);
    }

    @GetMapping("/{importJobId}")
    public ImportJobResponse getById(
            @PathVariable @Positive Long importJobId) {
        return importJobService.getById(importJobId);
    }

    @GetMapping("/{importJobId}/errors")
    public PageResponse<ImportRowErrorResponse> queryErrors(
            @PathVariable @Positive Long importJobId,
            @Valid @ModelAttribute
            ImportRowErrorQueryRequest request) {
        return importJobService.queryErrors(importJobId, request);
    }

    private Long authenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new IllegalStateException(
                    "Authenticated JWT subject is missing"
            );
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new NumberFormatException(
                        "JWT subject must be positive"
                );
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Authenticated JWT subject is invalid"
            );
        }
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null) {
            return null;
        }
        String normalized = originalFileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator < 0
                ? normalized
                : normalized.substring(separator + 1);
    }
}
