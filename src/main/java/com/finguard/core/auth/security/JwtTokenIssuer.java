package com.finguard.core.auth.security;

import com.finguard.core.auth.config.JwtProperties;
import com.finguard.core.auth.model.AuthenticatedUser;
import com.finguard.core.auth.validation.AuthInputNormalizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenIssuer(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            @Qualifier("jwtClock") Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Override
    public String issueToken(AuthenticatedUser authenticatedUser) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(
                jwtProperties.getExpiresInSeconds()
        );
        String normalizedUsername =
                AuthInputNormalizer.normalizeUsername(
                        authenticatedUser.username()
                );
        List<String> roles = authenticatedUser.roles()
                .stream()
                .map(Enum::name)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(authenticatedUser.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", normalizedUsername)
                .claim("roles", roles)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
