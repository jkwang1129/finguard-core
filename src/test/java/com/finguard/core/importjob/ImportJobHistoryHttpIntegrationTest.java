package com.finguard.core.importjob;
import com.finguard.core.testsupport.JobHistoryContract;
class ImportJobHistoryHttpIntegrationTest extends JobHistoryContract {
    protected String path(){ return "/api/import-jobs"; }
    protected String duplicateField(){ return "duplicateFile"; }
    protected long insertJob(long user,String hash){ return insertImport(user,hash); }
}
