package com.finguard.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoStaticResourceSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesDemoPageAnonymously() throws Exception {
        mockMvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML
                ))
                .andExpect(content().string(containsString("FinGuard Core")))
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsString(
                                StandardCharsets.UTF_8
                        ),
                        allOf(
                                containsString("ADMIN 登录"),
                                containsString("REVIEWER 登录"),
                                containsString("流程进度"),
                                containsString("我已了解演示会写入当前数据库")
                        )
                ));
    }

    @Test
    void demoPageDoesNotRelaxBusinessApiAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }
}
