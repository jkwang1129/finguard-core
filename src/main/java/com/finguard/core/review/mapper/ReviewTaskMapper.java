package com.finguard.core.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.review.entity.ReviewTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
