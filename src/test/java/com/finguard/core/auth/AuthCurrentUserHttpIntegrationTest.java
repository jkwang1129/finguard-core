package com.finguard.core.auth;

import com.finguard.core.auth.model.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthCurrentUserHttpIntegrationTest {

    private static final String ISSUER = "finguard-core";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void reviewerTokenReturnsOnlyNormalizedIdentityFields()
            throws Exception {
        String token = token(
                "reviewer.user",
                List.of("REVIEWER", "ADMIN", "REVIEWER")
        );

        mockMvc.perform(authenticatedGet(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("reviewer.user"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("REVIEWER"))
                .andExpect(jsonPath("$.roles[2]").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.signingKey").doesNotExist());
    }

    @Test
    void adminTokenReturnsOnlyAdminRole() throws Exception {
        String token = token("admin.user", List.of("ADMIN"));

        mockMvc.perform(authenticatedGet(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin.user"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").doesNotExist());
    }

    @Test
    void missingTokenPreservesAuthenticationRequiredContract()
            throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void invalidTokenPreservesInvalidTokenContract() throws Exception {
        mockMvc.perform(authenticatedGet("not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired access token"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void malformedAuthenticatedClaimsReturnStableServerError()
            throws Exception {
        String token = token(" ", List.of(RoleCode.ADMIN.name()));

        mockMvc.perform(authenticatedGet(token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected internal error occurred"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    authenticatedGet(String token) {
        return get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String token(String username, Object roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("42")
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(300))
                .claim("username", username)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
