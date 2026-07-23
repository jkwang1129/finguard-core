package com.finguard.core.account.service;

import com.finguard.core.account.dto.CreateAccountRequest;
import com.finguard.core.account.dto.AccountQueryRequest;
import com.finguard.core.account.dto.UpdateAccountNameRequest;
import com.finguard.core.account.dto.UpdateAccountStatusRequest;
import com.finguard.core.account.vo.AccountResponse;
import com.finguard.core.common.vo.PageResponse;

public interface AccountService {

    AccountResponse create(CreateAccountRequest request);

    AccountResponse getById(Long accountId);

    PageResponse<AccountResponse> query(AccountQueryRequest request);

    AccountResponse updateName(Long accountId, UpdateAccountNameRequest request);

    AccountResponse updateStatus(Long accountId, UpdateAccountStatusRequest request);

    void delete(Long accountId);
}
