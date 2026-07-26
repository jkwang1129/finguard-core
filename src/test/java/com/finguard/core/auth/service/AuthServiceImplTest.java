package com.finguard.core.auth.service;

import com.finguard.core.auth.config.JwtProperties;
import com.finguard.core.auth.dto.LoginRequest;
import com.finguard.core.auth.exception.InvalidCredentialsException;
import com.finguard.core.auth.model.AuthenticatedUser;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.security.AuthPrincipal;
import com.finguard.core.auth.security.TokenIssuer;
import com.finguard.core.auth.service.impl.AuthServiceImpl;
import com.finguard.core.auth.vo.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final AuthenticationManager authenticationManager =
            mock(AuthenticationManager.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setExpiresInSeconds(7200);
        authService = new AuthServiceImpl(
                authenticationManager,
                tokenIssuer,
                properties
        );
    }

    @Test
    void normalizesUsernameAuthenticatesAndIssuesTokenWithoutHash() {
        AuthPrincipal principal = principal(UserStatus.ACTIVE);
        Authentication successfulAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );
        when(authenticationManager.authenticate(any()))
                .thenReturn(successfulAuthentication);
        when(tokenIssuer.issueToken(any()))
                .thenReturn("signed-test-token");

        LoginResponse response = authService.login(
                new LoginRequest("  ALICE ", "Exact Password")
        );

        assertThat(response.getAccessToken())
                .isEqualTo("signed-test-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(7200);

        ArgumentCaptor<Authentication> authenticationCaptor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager)
                .authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue().getName())
                .isEqualTo("alice");
        assertThat(authenticationCaptor.getValue().getCredentials())
                .isEqualTo("Exact Password");

        ArgumentCaptor<AuthenticatedUser> userCaptor =
                ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(tokenIssuer).issueToken(userCaptor.capture());
        assertThat(userCaptor.getValue().id()).isEqualTo(7L);
        assertThat(userCaptor.getValue().username()).isEqualTo("alice");
        assertThat(userCaptor.getValue().roles())
                .containsExactly(RoleCode.ADMIN, RoleCode.REVIEWER);
        assertThat(AuthenticatedUser.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("password", "passwordHash");
    }

    @Test
    void unknownUsernameReturnsUnifiedCredentialsFailure() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("User not found"));

        assertUnifiedFailure();
    }

    @Test
    void wrongPasswordReturnsUnifiedCredentialsFailure() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertUnifiedFailure();
    }

    @Test
    void disabledUserReturnsUnifiedCredentialsFailure() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("User is disabled"));

        assertUnifiedFailure();
    }

    @Test
    void unexpectedPrincipalTypeRemainsAnInternalError() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("unexpected");
        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("alice", "Exact Password")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authentication principal has an unexpected type");
        verifyNoInteractions(tokenIssuer);
    }

    private void assertUnifiedFailure() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("alice", "Exact Password")
        ))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.MESSAGE);
        verifyNoInteractions(tokenIssuer);
    }

    private AuthPrincipal principal(UserStatus status) {
        return new AuthPrincipal(
                7L,
                "alice",
                "$2a$10$database-hash",
                status,
                List.of(RoleCode.REVIEWER, RoleCode.ADMIN)
        );
    }
}
