package com.finguard.core.auth.security;

import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AuthPrincipal
        implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String username;
    private final UserStatus status;
    private final List<RoleCode> roles;
    private String passwordHash;

    public AuthPrincipal(
            Long userId,
            String username,
            String passwordHash,
            UserStatus status,
            List<RoleCode> roles) {
        this.userId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        this.username = Objects.requireNonNull(
                username,
                "username must not be null"
        );
        this.passwordHash = Objects.requireNonNull(
                passwordHash,
                "passwordHash must not be null"
        );
        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
        );
        this.roles = Objects.requireNonNull(
                        roles,
                        "roles must not be null"
                )
                .stream()
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    public Long getUserId() {
        return userId;
    }

    public List<RoleCode> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                ))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "AuthPrincipal{"
                + "userId=" + userId
                + ", username='" + username + '\''
                + ", status=" + status
                + ", roles=" + roles
                + ", passwordHash='<redacted>'"
                + '}';
    }
}
