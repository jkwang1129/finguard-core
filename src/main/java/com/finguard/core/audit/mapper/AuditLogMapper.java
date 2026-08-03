package com.finguard.core.audit.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.audit.entity.AuditLog;
import com.finguard.core.audit.model.AuditActionCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO audit_logs (
                action_code,
                actor_type,
                actor_user_id,
                initiated_by,
                outcome,
                import_job_id,
                reconciliation_job_id,
                review_task_id,
                summary,
                created_at
            ) VALUES (
                #{actionCode},
                #{actorType},
                #{actorUserId},
                #{initiatedBy},
                #{outcome},
                #{importJobId},
                #{reconciliationJobId},
                #{reviewTaskId},
                #{summary},
                #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog auditLog);

    @Select("""
            <script>
            SELECT id,
                   action_code AS actionCode,
                   actor_type AS actorType,
                   actor_user_id AS actorUserId,
                   initiated_by AS initiatedBy,
                   outcome,
                   import_job_id AS importJobId,
                   reconciliation_job_id AS reconciliationJobId,
                   review_task_id AS reviewTaskId,
                   summary,
                   created_at AS createdAt
            FROM audit_logs
            WHERE 1 = 1
            <if test="actionCode != null">
              AND action_code = #{actionCode}
            </if>
            <if test="initiatedBy != null">
              AND initiated_by = #{initiatedBy}
            </if>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    IPage<AuditLog> selectPage(
            Page<AuditLog> page,
            @Param("actionCode") AuditActionCode actionCode,
            @Param("initiatedBy") Long initiatedBy
    );
}
