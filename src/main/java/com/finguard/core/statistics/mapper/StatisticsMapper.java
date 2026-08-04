package com.finguard.core.statistics.mapper;

import com.finguard.core.statistics.model.StatusCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatisticsMapper {

    @Select("""
            SELECT status AS category, COUNT(*) AS countValue
            FROM import_jobs
            GROUP BY status
            """)
    List<StatusCountRow> countImportJobsByStatus();

    @Select("""
            SELECT status AS category, COUNT(*) AS countValue
            FROM reconciliation_jobs
            GROUP BY status
            """)
    List<StatusCountRow> countReconciliationJobsByStatus();

    @Select("""
            SELECT result_type AS category, COUNT(*) AS countValue
            FROM reconciliation_results
            GROUP BY result_type
            """)
    List<StatusCountRow> countReconciliationResultsByType();

    @Select("""
            SELECT rule_code AS category, COUNT(*) AS countValue
            FROM risk_hits
            GROUP BY rule_code
            """)
    List<StatusCountRow> countRiskHitsByRule();

    @Select("""
            SELECT status AS category, COUNT(*) AS countValue
            FROM review_tasks
            GROUP BY status
            """)
    List<StatusCountRow> countReviewTasksByStatus();
}
