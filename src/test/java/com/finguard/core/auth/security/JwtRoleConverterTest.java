package com.finguard.core.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void convertsOnlyKnownRolesAndUsesUsernameAsPrincipal() {
        Jwt jwt = jwt(Map.of(
                "sub", "42",
                "username", "alice",
                "roles", List.of(
                        "REVIEWER",
                        "ADMIN",
                        "ADMIN",
                        "UNTRUSTED"
                )
        ));

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities()).containsExactly(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_REVIEWER")
        );
    }

    @Test
    void missingRolesGrantsNoAuthorityAndFallsBackToSubject() {
        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(
                        jwt(Map.of("sub", "42"))
                );

        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant issuedAt = Instant.parse("2026-07-26T08:00:00Z");
        return new Jwt(
                "test-token",
                issuedAt,
                issuedAt.plusSeconds(7200),
                Map.of("alg", "HS256"),
                claims
        );
    }
}
