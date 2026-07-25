package com.finguard.core.auth.model;

import java.util.List;

public class AuthUserRecord {

    private Long id;
    private String username;
    private String passwordHash;
    private UserStatus status;
    private List<RoleCode> roles;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public List<RoleCode> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleCode> roles) {
        this.roles = roles;
    }
}
