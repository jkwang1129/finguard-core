package com.finguard.core.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.finguard.core.auth.config.JwtProperties;
import com.finguard.core.auth.model.AuthenticatedUser;
import com.finguard.core.auth.model.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenIssuerTest {

    private static final Instant ISSUED_AT =
            Instant.parse("2026-07-26T08:00:00Z");
    private static final String SECRET_TEXT =
            "finguard-task5-test-secret-key!!";

    private SecretKey secretKey;
    private JwtTokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        secretKey = new SecretKeySpec(
                SECRET_TEXT.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("finguard-core");
        properties.setExpiresInSeconds(7200);
        tokenIssuer = new JwtTokenIssuer(
                new NimbusJwtEncoder(
                        new ImmutableSecret<>(secretKey)
                ),
                properties,
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void signsHs256TokenWithExactSafeClaimsAndTwoHourExpiry() {
        String token = tokenIssuer.issueToken(
                new AuthenticatedUser(
                        42L,
                        "  ALICE ",
                        List.of(
                                RoleCode.REVIEWER,
                                RoleCode.ADMIN,
                                RoleCode.ADMIN
                        )
                )
        );

        Jwt jwt = decoder(secretKey).decode(token);

        assertThat(jwt.getHeaders())
                .containsEntry("alg", "HS256")
                .containsEntry("typ", "JWT");
        assertThat(jwt.getClaimAsString("iss"))
                .isEqualTo("finguard-core");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(jwt.getExpiresAt())
                .isEqualTo(ISSUED_AT.plusSeconds(7200));
        assertThat(jwt.getClaimAsString("username"))
                .isEqualTo("alice");
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("ADMIN", "REVIEWER");
        assertThat(jwt.getClaims().keySet()).isEqualTo(Set.of(
                "iss",
                "sub",
                "iat",
                "exp",
                "username",
                "roles"
        ));
        assertThat(jwt.getClaims()).doesNotContainKeys(
                "password",
                "passwordHash",
                "account",
                "accounts",
                "transactions"
        );
    }

    @Test
    void rejectsTokenWhenVerifiedWithDifferentSecret() {
        String token = tokenIssuer.issueToken(
                new AuthenticatedUser(
                        42L,
                        "alice",
                        List.of(RoleCode.ADMIN)
                )
        );
        SecretKey wrongSecret = new SecretKeySpec(
                "different-task5-test-secret-key!"
                        .getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        assertThatThrownBy(() -> decoder(wrongSecret).decode(token))
                .isInstanceOf(JwtException.class);
    }

    private NimbusJwtDecoder decoder(SecretKey verificationKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(verificationKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                jwt -> OAuth2TokenValidatorResult.success()
        );
        return decoder;
    }
}
