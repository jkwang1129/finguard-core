package com.finguard.core.auth.vo;

import com.finguard.core.auth.model.RoleCode;

import java.util.List;

public record CurrentUserResponse(
        String username,
        List<RoleCode> roles
) {
}
