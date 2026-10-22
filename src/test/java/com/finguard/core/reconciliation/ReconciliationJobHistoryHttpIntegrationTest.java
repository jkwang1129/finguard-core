package com.finguard.core.reconciliation;
import com.finguard.core.testsupport.JobHistoryContract;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ReconciliationJobHistoryHttpIntegrationTest extends JobHistoryContract {
    protected String path(){ return "/api/reconciliation-jobs"; }
    protected String duplicateField(){ return "duplicateRequest"; }
    @Test void filtersByImportJobAndValidatesItsId() throws Exception {
        long owner=user();
        String hash=java.util.UUID.randomUUID().toString().replace("-", "").repeat(2);
        long job=insertJob(owner,hash);
        long importId=jdbc.queryForObject("SELECT import_job_id FROM reconciliation_jobs WHERE id=?",Long.class,job);
        mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_REVIEWER")))
            .param("importJobId",String.valueOf(importId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.records[0].id").value(job));
        mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .param("importJobId","0")).andExpect(status().isBadRequest());
    }
    protected long insertJob(long user,String hash){
        long id=insertImport(user,hash);
        jdbc.update("INSERT INTO reconciliation_jobs(import_job_id,status,created_by,created_at) VALUES (?,'PENDING',?,'2026-10-09 00:00:00')",id,user);
        return jdbc.queryForObject("SELECT id FROM reconciliation_jobs WHERE import_job_id=?",Long.class,id);
    }
}
