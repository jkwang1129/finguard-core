package com.finguard.core.account.controller;

import com.finguard.core.account.dto.AccountQueryRequest;
import com.finguard.core.account.dto.CreateAccountRequest;
import com.finguard.core.account.dto.UpdateAccountNameRequest;
import com.finguard.core.account.dto.UpdateAccountStatusRequest;
import com.finguard.core.account.service.AccountService;
import com.finguard.core.account.vo.AccountResponse;
import com.finguard.core.common.vo.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Account management")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(summary = "Create an account")
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get an account by ID")
    public AccountResponse getById(
            @PathVariable @Positive Long accountId) {
        return accountService.getById(accountId);
    }

    @GetMapping
    @Operation(summary = "Query accounts")
    public PageResponse<AccountResponse> query(
            @Valid @ModelAttribute AccountQueryRequest request) {
        return accountService.query(request);
    }

    @PatchMapping("/{accountId}/name")
    @Operation(summary = "Update an account name")
    public AccountResponse updateName(
            @PathVariable @Positive Long accountId,
            @Valid @RequestBody UpdateAccountNameRequest request) {
        return accountService.updateName(accountId, request);
    }

    @PatchMapping("/{accountId}/status")
    @Operation(summary = "Update an account status")
    public AccountResponse updateStatus(
            @PathVariable @Positive Long accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountService.updateStatus(accountId, request);
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Delete an unused account")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long accountId) {
        accountService.delete(accountId);
        return ResponseEntity.noContent().build();
    }
}
