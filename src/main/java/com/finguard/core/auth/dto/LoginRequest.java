package com.finguard.core.auth.dto;

import com.finguard.core.auth.validation.NormalizedUsernameLength;
import com.finguard.core.auth.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "username must not be blank")
    @NormalizedUsernameLength(
            min = 3,
            max = 64,
            message = "username length must be between 3 and 64 "
                    + "after normalization"
    )
    private String username;

    @NotBlank(message = "password must not be blank")
    @Utf8ByteLength(
            min = 1,
            max = 72,
            message = "password must be between 1 and 72 UTF-8 bytes"
    )
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "LoginRequest{"
                + "username='" + username + '\''
                + ", password='<redacted>'"
                + '}';
    }
}
