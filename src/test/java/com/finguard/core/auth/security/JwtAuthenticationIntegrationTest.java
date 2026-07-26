package com.finguard.core.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtAuthenticationIntegrationTest.SecurityProbeConfiguration.class)
class JwtAuthenticationIntegrationTest {

    private static final String ISSUER = "finguard-core";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void healthAndLoginRemainAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingTokenReturnsAuthenticationRequired() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required"))
                .andExpect(jsonPath("$.path")
                        .value("/api/accounts"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void malformedTokenReturnsInvalidToken() throws Exception {
        assertInvalidToken("not-a-jwt");
    }

    @Test
    void tamperedSignatureReturnsInvalidToken() throws Exception {
        String validToken = token(
                jwtEncoder,
                ISSUER,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300)
        );
        char finalCharacter =
                validToken.charAt(validToken.length() - 1);
        char replacement = finalCharacter == 'a' ? 'b' : 'a';
        String tamperedToken = validToken.substring(
                0,
                validToken.length() - 1
        ) + replacement;

        assertInvalidToken(tamperedToken);
    }

    @Test
    void wrongSigningKeyReturnsInvalidToken() throws Exception {
        byte[] wrongSecret = "different-day4-test-only-secret-key"
                .getBytes(StandardCharsets.UTF_8);
        JwtEncoder wrongEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(
                        new SecretKeySpec(wrongSecret, "HmacSHA256")
                )
        );
        assertInvalidToken(token(
                wrongEncoder,
                ISSUER,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300)
        ));
    }

    @Test
    void wrongIssuerReturnsInvalidToken() throws Exception {
        assertInvalidToken(token(
                jwtEncoder,
                "other-issuer",
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300)
        ));
    }

    @Test
    void expiredTokenReturnsInvalidToken() throws Exception {
        assertInvalidToken(token(
                jwtEncoder,
                ISSUER,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        ));
    }

    @Test
    void validTokenBuildsIdentityWithoutServerSession()
            throws Exception {
        String validToken = token(
                jwtEncoder,
                ISSUER,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300)
        );

        mockMvc.perform(get("/test/security/identity")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validToken
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.name")
                        .value("day4-user"))
                .andExpect(jsonPath("$.authorities[0]")
                        .value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.authorities[1]")
                        .value("ROLE_REVIEWER"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(result -> assertThat(
                        result.getRequest().getSession(false)
                ).isNull());

        mockMvc.perform(get("/api/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validToken
                        ))
                .andExpect(status().isOk());
    }

    private void assertInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired access token"))
                .andExpect(jsonPath("$.path")
                        .value("/api/accounts"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    private String token(
            JwtEncoder encoder,
            String issuer,
            Instant issuedAt,
            Instant expiresAt) {
        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", "day4-user")
                .claim(
                        "roles",
                        List.of("REVIEWER", "ADMIN", "ADMIN")
                )
                .build();
        return encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityProbeConfiguration {

        @Bean
        SecurityProbeController securityProbeController() {
            return new SecurityProbeController();
        }
    }

    @RestController
    static class SecurityProbeController {

        @GetMapping("/test/security/identity")
        Map<String, Object> identity(Authentication authentication) {
            List<String> authorities = authentication
                    .getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            return Map.of(
                    "name",
                    authentication.getName(),
                    "authorities",
                    authorities
            );
        }
    }
}
