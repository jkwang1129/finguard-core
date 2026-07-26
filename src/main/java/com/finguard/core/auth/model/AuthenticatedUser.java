package com.finguard.core.auth.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record AuthenticatedUser(
        Long id,
        String username,
        List<RoleCode> roles
) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        roles = roles.stream()
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }
}
