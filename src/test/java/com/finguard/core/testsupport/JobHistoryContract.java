package com.finguard.core.testsupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.core.auth.support.AuthTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @Transactional
public abstract class JobHistoryContract {
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    protected abstract String path();
    protected abstract String duplicateField();
    protected abstract long insertJob(long user, String hash);
    protected long insertImport(long user, String hash) {
        jdbc.update("INSERT INTO import_jobs(original_file_name,file_hash,file_size_bytes,status,created_by,created_at) VALUES ('history.csv',?,1,'PENDING',?,'2026-10-09 00:00:00')", hash, user);
        return jdbc.queryForObject("SELECT id FROM import_jobs WHERE file_hash=?", Long.class, hash);
    }
    protected long user() {
        String name="history-"+UUID.randomUUID();
        jdbc.update("INSERT INTO users(username,password_hash,status) VALUES (?,?,'ACTIVE')",name,AuthTestFixture.TEST_PASSWORD_HASH);
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
    }
    @Test void historySupportsStablePaginationAndFilters() throws Exception {
        long owner=user();
        List<Long> expected=new ArrayList<>();
        for(int i=0;i<101;i++) expected.add(insertJob(owner, UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","")));
        Collections.reverse(expected);
        List<Long> actual=new ArrayList<>();
        for(int page=1;page<=2;page++) {
            var result=mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_REVIEWER")))
                .param("createdBy",String.valueOf(owner)).param("size","100").param("page",String.valueOf(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(101)).andReturn();
            for(var item:json.readTree(result.getResponse().getContentAsString()).path("records")) {
                actual.add(item.path("id").asLong());
                assertThat(item.path(duplicateField()).asBoolean()).isFalse();
            }
        }
        assertThat(actual).containsExactlyElementsOf(expected);
        mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).param("createdBy",String.valueOf(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.records.length()").value(20));
        mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).param("createdBy",String.valueOf(owner)).param("status","PROCESSING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.records.length()").value(0));
    }
    @Test void historyRejectsInvalidParameters() throws Exception {
        for(String[] query:List.of(new String[]{"size","101"},new String[]{"page","0"},new String[]{"createdBy","0"},new String[]{"status","INVALID"}))
            mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).param(query[0],query[1])).andExpect(status().isBadRequest());
    }
    @Test void historyEnforcesReadRoles() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        for(String role:List.of("ADMIN","REVIEWER"))
            mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_"+role)))).andExpect(status().isOk());
        mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER")))).andExpect(status().isForbidden());
    }
}
