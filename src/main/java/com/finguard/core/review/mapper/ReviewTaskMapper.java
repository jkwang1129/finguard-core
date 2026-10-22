package com.finguard.core.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.entity.ReviewTask;
import com.finguard.core.review.model.ReviewTaskContextView;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.review.model.ReviewTaskView;
import com.finguard.core.risk.model.RiskRuleCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    @Insert("""
            <script>
            INSERT INTO review_tasks (
                source_type,
                reconciliation_result_id,
                risk_hit_id,
                status,
                version
            )
            VALUES
            <foreach collection="tasks" item="task" separator=",">
                (
                    #{task.sourceType},
                    #{task.reconciliationResultId},
                    #{task.riskHitId},
                    #{task.status},
                    #{task.version}
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("tasks") List<ReviewTask> tasks);

    @Select("""
            <script>
            SELECT id,
                   source_type AS sourceType,
                   reconciliation_result_id AS reconciliationResultId,
                   risk_hit_id AS riskHitId,
                   status,
                   version,
                   reviewed_by AS reviewedBy,
                   reviewed_at AS reviewedAt,
                   decision_note AS decisionNote,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM review_tasks
            WHERE reconciliation_result_id IN
            <foreach collection="ids" item="id"
                     open="(" separator="," close=")">
                #{id}
            </foreach>
            ORDER BY reconciliation_result_id ASC, id ASC
            </script>
            """)
    List<ReviewTask> selectByReconciliationResultIds(
            @Param("ids") List<Long> ids
    );

    @Select("""
            <script>
            SELECT id,
                   source_type AS sourceType,
                   reconciliation_result_id AS reconciliationResultId,
                   risk_hit_id AS riskHitId,
                   status,
                   version,
                   reviewed_by AS reviewedBy,
                   reviewed_at AS reviewedAt,
                   decision_note AS decisionNote,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM review_tasks
            WHERE risk_hit_id IN
            <foreach collection="ids" item="id"
                     open="(" separator="," close=")">
                #{id}
            </foreach>
            ORDER BY risk_hit_id ASC, id ASC
            </script>
            """)
    List<ReviewTask> selectByRiskHitIds(@Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT rt.id,
                   rt.source_type AS sourceType,
                   rt.reconciliation_result_id AS reconciliationResultId,
                   rt.risk_hit_id AS riskHitId,
                   COALESCE(
                       direct_result.csv_transaction_id,
                       risk_result.csv_transaction_id
                   ) AS csvTransactionId,
                   COALESCE(
                       direct_result.result_type,
                       risk_result.result_type
                   ) AS resultType,
                   rh.rule_code AS ruleCode,
                   rh.reason_code AS reasonCode,
                   rt.status,
                   rt.version,
                   rt.reviewed_by AS reviewedBy,
                   rt.reviewed_at AS reviewedAt,
                   rt.decision_note AS decisionNote,
                   rt.created_at AS createdAt,
                   rt.updated_at AS updatedAt
            FROM review_tasks rt
            LEFT JOIN reconciliation_results direct_result
              ON direct_result.id = rt.reconciliation_result_id
            LEFT JOIN risk_hits rh
              ON rh.id = rt.risk_hit_id
            LEFT JOIN reconciliation_results risk_result
              ON risk_result.id = rh.reconciliation_result_id
            WHERE 1 = 1
            <if test="status != null">
              AND rt.status = #{status}
            </if>
            <if test="sourceType != null">
              AND rt.source_type = #{sourceType}
            </if>
            <if test="resultType != null">
              AND direct_result.result_type = #{resultType}
            </if>
            <if test="ruleCode != null">
              AND rh.rule_code = #{ruleCode}
            </if>
            ORDER BY rt.created_at DESC, rt.id DESC
            </script>
            """)
    IPage<ReviewTaskView> selectTaskPage(
            Page<ReviewTaskView> page,
            @Param("status") ReviewTaskStatus status,
            @Param("sourceType") ReviewTaskSourceType sourceType,
            @Param("resultType") ReconciliationResultType resultType,
            @Param("ruleCode") RiskRuleCode ruleCode
    );

    @Select("""
            SELECT rt.id,
                   rt.source_type AS sourceType,
                   rt.reconciliation_result_id AS reconciliationResultId,
                   rt.risk_hit_id AS riskHitId,
                   COALESCE(
                       direct_result.csv_transaction_id,
                       risk_result.csv_transaction_id
                   ) AS csvTransactionId,
                   COALESCE(
                       direct_result.result_type,
                       risk_result.result_type
                   ) AS resultType,
                   rh.rule_code AS ruleCode,
                   rh.reason_code AS reasonCode,
                   rt.status,
                   rt.version,
                   rt.reviewed_by AS reviewedBy,
                   rt.reviewed_at AS reviewedAt,
                   rt.decision_note AS decisionNote,
                   rt.created_at AS createdAt,
                   rt.updated_at AS updatedAt
            FROM review_tasks rt
            LEFT JOIN reconciliation_results direct_result
              ON direct_result.id = rt.reconciliation_result_id
            LEFT JOIN risk_hits rh
              ON rh.id = rt.risk_hit_id
            LEFT JOIN reconciliation_results risk_result
              ON risk_result.id = rh.reconciliation_result_id
            WHERE rt.id = #{reviewTaskId}
            """)
    ReviewTaskView selectTaskViewById(
            @Param("reviewTaskId") Long reviewTaskId
    );

    @Select("""
            SELECT rt.id AS reviewTaskId,
                   rt.version AS reviewTaskVersion,
                   rt.status AS reviewTaskStatus,
                   rh.rule_code AS ruleCode,
                   COALESCE(
                       rh.reason_code,
                       rr.reason_code
                   ) AS taskReasonCode,
                   rt.created_at AS reviewTaskCreatedAt,
                   t.id AS transactionId,
                   t.account_id AS accountId,
                   t.amount AS transactionAmount,
                   a.currency,
                   t.transaction_time AS occurredAt,
                   t.source AS transactionSourceType,
                   rr.result_type AS reconciliationResultType,
                   rr.match_method AS matchMethod,
                   rr.reason_code AS reconciliationReasonCode,
                   rh.observed_amount AS observedAmount,
                   rh.threshold_amount AS thresholdAmount,
                   rh.observed_count AS observedCount,
                   rh.threshold_count AS thresholdCount,
                   rh.window_seconds AS windowSeconds,
                   a.status AS accountStatus,
                   a.account_name AS accountDisplayName
            FROM review_tasks rt
            LEFT JOIN risk_hits rh
              ON rh.id = rt.risk_hit_id
            INNER JOIN reconciliation_results rr
              ON rr.id = COALESCE(
                  rt.reconciliation_result_id,
                  rh.reconciliation_result_id
              )
            INNER JOIN transactions t
              ON t.id = rr.csv_transaction_id
            INNER JOIN accounts a
              ON a.id = t.account_id
            WHERE rt.id = #{reviewTaskId}
            """)
    ReviewTaskContextView selectContextById(
            @Param("reviewTaskId") Long reviewTaskId
    );

    @Update("""
            UPDATE review_tasks
            SET status = #{status},
                version = version + 1,
                reviewed_by = #{reviewerId},
                reviewed_at = #{reviewedAt},
                decision_note = #{decisionNote},
                updated_at = #{reviewedAt}
            WHERE id = #{reviewTaskId}
              AND status = 'PENDING'
              AND version = #{expectedVersion}
            """)
    int updateDecision(
            @Param("reviewTaskId") Long reviewTaskId,
            @Param("status") ReviewTaskStatus status,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("reviewerId") Long reviewerId,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("decisionNote") String decisionNote
    );
}
