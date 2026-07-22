package com.finguard.core.account.service;

import com.finguard.core.account.dto.CreateAccountRequest;
import com.finguard.core.account.dto.UpdateAccountNameRequest;
import com.finguard.core.account.dto.UpdateAccountStatusRequest;
import com.finguard.core.account.vo.AccountResponse;

public interface AccountService {

    AccountResponse create(CreateAccountRequest request);

    AccountResponse getById(Long accountId);

    AccountResponse updateName(Long accountId, UpdateAccountNameRequest request);

    AccountResponse updateStatus(Long accountId, UpdateAccountStatusRequest request);

    void delete(Long accountId);
}
