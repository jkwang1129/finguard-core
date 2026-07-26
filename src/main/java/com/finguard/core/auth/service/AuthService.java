package com.finguard.core.auth.service;

import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.vo.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
