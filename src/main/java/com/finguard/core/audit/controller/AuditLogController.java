package com.finguard.core.audit.controller;

import com.finguard.core.audit.dto.AuditLogQueryRequest;
import com.finguard.core.audit.service.AuditLogService;
import com.finguard.core.audit.vo.AuditLogResponse;
import com.finguard.core.common.vo.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "Critical business audit records")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(summary = "Query audit logs")
    public PageResponse<AuditLogResponse> query(
            @Valid @ModelAttribute AuditLogQueryRequest request) {
        return auditLogService.query(request);
    }
}
