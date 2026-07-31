package com.finguard.core.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.reconciliation.entity.ReconciliationResult;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReconciliationResultMapper
        extends BaseMapper<ReconciliationResult> {

    @Insert("""
            <script>
            INSERT INTO reconciliation_results (
                reconciliation_job_id,
                csv_transaction_id,
                manual_transaction_id,
                result_type,
                match_method,
                reason_code
            )
            VALUES
            <foreach collection="results" item="result" separator=",">
                (
                    #{result.reconciliationJobId},
                    #{result.csvTransactionId},
                    #{result.manualTransactionId},
                    #{result.resultType},
                    #{result.matchMethod},
                    #{result.reasonCode}
                )
            </foreach>
            </script>
            """)
    int insertBatch(
            @Param("results") List<ReconciliationResult> results
    );

    @Select("""
            <script>
            SELECT id,
                   reconciliation_job_id AS reconciliationJobId,
                   csv_transaction_id AS csvTransactionId,
                   manual_transaction_id AS manualTransactionId,
                   result_type AS resultType,
                   match_method AS matchMethod,
                   reason_code AS reasonCode,
                   created_at AS createdAt
            FROM reconciliation_results
            WHERE reconciliation_job_id = #{reconciliationJobId}
            <if test="resultType != null">
              AND result_type = #{resultType}
            </if>
            ORDER BY csv_transaction_id ASC, id ASC
            </script>
            """)
    IPage<ReconciliationResult> selectPageByJobId(
            Page<ReconciliationResult> page,
            @Param("reconciliationJobId") Long reconciliationJobId,
            @Param("resultType") ReconciliationResultType resultType
    );
}
