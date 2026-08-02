package com.finguard.core.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.risk.entity.RiskHit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RiskHitMapper extends BaseMapper<RiskHit> {

    @Insert("""
            <script>
            INSERT INTO risk_hits (
                reconciliation_result_id,
                rule_code,
                reason_code,
                observed_amount,
                threshold_amount,
                observed_count,
                threshold_count,
                window_seconds,
                reason_summary
            )
            VALUES
            <foreach collection="hits" item="hit" separator=",">
                (
                    #{hit.reconciliationResultId},
                    #{hit.ruleCode},
                    #{hit.reasonCode},
                    #{hit.observedAmount},
                    #{hit.thresholdAmount},
                    #{hit.observedCount},
                    #{hit.thresholdCount},
                    #{hit.windowSeconds},
                    #{hit.reasonSummary}
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("hits") List<RiskHit> hits);

    @Select("""
            <script>
            SELECT id,
                   reconciliation_result_id AS reconciliationResultId,
                   rule_code AS ruleCode,
                   reason_code AS reasonCode,
                   observed_amount AS observedAmount,
                   threshold_amount AS thresholdAmount,
                   observed_count AS observedCount,
                   threshold_count AS thresholdCount,
                   window_seconds AS windowSeconds,
                   reason_summary AS reasonSummary,
                   created_at AS createdAt
            FROM risk_hits
            WHERE reconciliation_result_id IN
            <foreach collection="reconciliationResultIds"
                     item="reconciliationResultId"
                     open="("
                     separator=","
                     close=")">
                #{reconciliationResultId}
            </foreach>
            ORDER BY reconciliation_result_id ASC,
                     FIELD(
                         rule_code,
                         'LARGE_AMOUNT',
                         'POSSIBLE_DUPLICATE',
                         'FREQUENT_TRANSACTION'
                     ) ASC,
                     id ASC
            </script>
            """)
    List<RiskHit> selectByReconciliationResultIds(
            @Param("reconciliationResultIds")
            List<Long> reconciliationResultIds
    );
}
