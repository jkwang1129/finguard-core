package com.finguard.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DemoStaticResourceSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Test void servesDemoPageAnonymously() throws Exception {
        mockMvc.perform(get("/demo/index.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("FinGuard Core")))
            .andExpect(content().string(containsString("type=\"module\"")))
            .andExpect(content().string(containsString("persistence-ack")));
    }
    @Test void servesExternalStylesAndSharedModulesAnonymously() throws Exception {
        for (String resource : new String[]{"styles.css","js/app.js","js/api.js","js/ui.js","js/session.js","js/lifecycle.js"})
            mockMvc.perform(get("/demo/" + resource)).andExpect(status().isOk());
    }
    @Test void anonymousBusinessRequestsRemainProtected() throws Exception {
        for (String path : new String[]{"accounts","import-jobs","reconciliation-jobs","auth/me"})
            mockMvc.perform(get("/api/" + path)).andExpect(status().isUnauthorized());
    }
    @Test void anonymousWritesToStaticPathsAreNotPublic() throws Exception {
        mockMvc.perform(post("/demo/index.html")).andExpect(status().isUnauthorized());
    }
}
