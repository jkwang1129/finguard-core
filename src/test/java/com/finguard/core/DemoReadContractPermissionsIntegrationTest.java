package com.finguard.core;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DemoReadContractPermissionsIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void authenticatedMeUsesServerIdentity() throws Exception {
        mvc.perform(get("/api/auth/me").with(jwt().jwt(j -> j.subject("1")
            .claim("username", "console-reviewer").claim("roles", java.util.List.of("REVIEWER")))
            .authorities(new SimpleGrantedAuthority("ROLE_REVIEWER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("console-reviewer"));
    }
    @Test void adminCannotReadReviewerContext() throws Exception {
        mvc.perform(get("/api/review-tasks/1/context").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden());
    }
    @Test void staticDemoRemainsPublicButIdentityDoesNot() throws Exception {
        mvc.perform(get("/demo/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
