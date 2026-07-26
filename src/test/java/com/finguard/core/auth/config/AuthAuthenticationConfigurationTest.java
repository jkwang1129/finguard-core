package com.finguard.core.auth.config;

import com.finguard.core.auth.mapper.UserMapper;
import com.finguard.core.auth.model.AuthUserRecord;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.security.AuthPrincipal;
import com.finguard.core.auth.security.DatabaseUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthAuthenticationConfigurationTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder =
            new PasswordConfiguration().passwordEncoder();
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        DatabaseUserDetailsService userDetailsService =
                new DatabaseUserDetailsService(userMapper);
        AuthAuthenticationConfiguration configuration =
                new AuthAuthenticationConfiguration();
        DaoAuthenticationProvider provider =
                configuration.daoAuthenticationProvider(
                        userDetailsService,
                        passwordEncoder
                );
        authenticationManager =
                configuration.authenticationManager(provider);
    }

    @Test
    void authenticatesWithBcryptAndErasesStoredHashFromPrincipal() {
        when(userMapper.findAuthUserByNormalizedUsername("alice"))
                .thenReturn(record(
                        UserStatus.ACTIVE,
                        passwordEncoder.encode("Correct-Password")
                ));

        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken
                                .unauthenticated(
                                        "alice",
                                        "Correct-Password"
                                )
                );

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal())
                .isInstanceOf(AuthPrincipal.class);
        AuthPrincipal principal =
                (AuthPrincipal) authentication.getPrincipal();
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getUserId()).isEqualTo(7L);
    }

    @Test
    void rejectsWrongPasswordWithoutReturningStoredHash() {
        String passwordHash =
                passwordEncoder.encode("Correct-Password");
        when(userMapper.findAuthUserByNormalizedUsername("alice"))
                .thenReturn(record(
                        UserStatus.ACTIVE,
                        passwordHash
                ));

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "alice",
                        "Wrong-Password"
                )
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageNotContaining(passwordHash);
    }

    @Test
    void rejectsDisabledUserWithSpringAccountStatus() {
        when(userMapper.findAuthUserByNormalizedUsername("alice"))
                .thenReturn(record(
                        UserStatus.DISABLED,
                        passwordEncoder.encode("Correct-Password")
                ));

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "alice",
                        "Correct-Password"
                )
        )).isInstanceOf(DisabledException.class);
    }

    @Test
    void hidesWhetherUsernameExists() {
        when(userMapper.findAuthUserByNormalizedUsername("missing"))
                .thenReturn(null);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "missing",
                        "Any-Password"
                )
        )).isInstanceOf(BadCredentialsException.class);
    }

    private AuthUserRecord record(
            UserStatus status,
            String passwordHash) {
        AuthUserRecord record = new AuthUserRecord();
        record.setId(7L);
        record.setUsername("alice");
        record.setPasswordHash(passwordHash);
        record.setStatus(status);
        record.setRoles(List.of(RoleCode.ADMIN));
        return record;
    }
}
