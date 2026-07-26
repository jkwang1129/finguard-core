package com.finguard.core.auth.security;

import com.finguard.core.auth.mapper.UserMapper;
import com.finguard.core.auth.model.AuthUserRecord;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final DatabaseUserDetailsService userDetailsService =
            new DatabaseUserDetailsService(userMapper);

    @Test
    void loadsNormalizedActiveUserWithStableDistinctAuthorities() {
        when(userMapper.findAuthUserByNormalizedUsername("alice"))
                .thenReturn(record(
                        UserStatus.ACTIVE,
                        List.of(
                                RoleCode.REVIEWER,
                                RoleCode.ADMIN,
                                RoleCode.ADMIN
                        )
                ));

        AuthPrincipal principal = (AuthPrincipal) userDetailsService
                .loadUserByUsername("  ALICE ");

        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$test");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getRoles())
                .containsExactly(RoleCode.ADMIN, RoleCode.REVIEWER);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN", "ROLE_REVIEWER");
        assertThat(principal.toString())
                .doesNotContain("$2a$10$test")
                .contains("passwordHash='<redacted>'");
        verify(userMapper)
                .findAuthUserByNormalizedUsername("alice");
    }

    @Test
    void mapsDisabledDatabaseStatusToDisabledPrincipal() {
        when(userMapper.findAuthUserByNormalizedUsername("alice"))
                .thenReturn(record(
                        UserStatus.DISABLED,
                        List.of(RoleCode.REVIEWER)
                ));

        AuthPrincipal principal = (AuthPrincipal) userDetailsService
                .loadUserByUsername("alice");

        assertThat(principal.isEnabled()).isFalse();
    }

    @Test
    void missingUserProducesGenericSpringSecurityException() {
        when(userMapper.findAuthUserByNormalizedUsername("missing"))
                .thenReturn(null);

        assertThatThrownBy(() -> userDetailsService
                .loadUserByUsername(" Missing "))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Authentication user was not found")
                .hasMessageNotContaining("missing");
    }

    @Test
    void erasesPasswordHashAfterAuthenticationLifecycle() {
        AuthPrincipal principal = new AuthPrincipal(
                7L,
                "alice",
                "$2a$10$test",
                UserStatus.ACTIVE,
                List.of(RoleCode.ADMIN)
        );

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    private AuthUserRecord record(
            UserStatus status,
            List<RoleCode> roles) {
        AuthUserRecord record = new AuthUserRecord();
        record.setId(7L);
        record.setUsername("alice");
        record.setPasswordHash("$2a$10$test");
        record.setStatus(status);
        record.setRoles(roles);
        return record;
    }
}
