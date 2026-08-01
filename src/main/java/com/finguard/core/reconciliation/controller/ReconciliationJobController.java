package com.finguard.core.reconciliation.controller;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.reconciliation.dto.CreateReconciliationJobRequest;
import com.finguard.core.reconciliation.dto.ReconciliationResultQueryRequest;
import com.finguard.core.reconciliation.service.ReconciliationJobService;
import com.finguard.core.reconciliation.vo.ReconciliationJobResponse;
import com.finguard.core.reconciliation.vo.ReconciliationResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/reconciliation-jobs")
public class ReconciliationJobController {

    private final ReconciliationJobService reconciliationJobService;

    public ReconciliationJobController(
            ReconciliationJobService reconciliationJobService) {
        this.reconciliationJobService = reconciliationJobService;
    }

    @PostMapping
    public ResponseEntity<ReconciliationJobResponse> create(
            @Valid @RequestBody
            CreateReconciliationJobRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ReconciliationJobResponse response =
                reconciliationJobService.create(
                        request.importJobId(),
                        authenticatedUserId(jwt)
                );
        if (response.duplicateRequest()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header(
                        HttpHeaders.LOCATION,
                        URI.create(
                                "/api/reconciliation-jobs/"
                                        + response.id()
                        ).toString()
                )
                .body(response);
    }

    @GetMapping("/{reconciliationJobId}")
    public ReconciliationJobResponse getById(
            @PathVariable @Positive Long reconciliationJobId) {
        return reconciliationJobService.getById(
                reconciliationJobId
        );
    }

    @GetMapping("/{reconciliationJobId}/results")
    public PageResponse<ReconciliationResultResponse> queryResults(
            @PathVariable @Positive Long reconciliationJobId,
            @Valid @ModelAttribute
            ReconciliationResultQueryRequest request) {
        return reconciliationJobService.queryResults(
                reconciliationJobId,
                request
        );
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
}
