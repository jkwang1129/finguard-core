package com.finguard.core.messaging.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.messaging.outbox.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            SELECT id,
                   event_type AS eventType,
                   aggregate_id AS aggregateId,
                   schema_version AS schemaVersion,
                   status,
                   attempts,
                   next_attempt_at AS nextAttemptAt,
                   last_error_summary AS lastErrorSummary,
                   sent_at AS sentAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM outbox_events
            WHERE status IN ('NEW', 'RETRY')
              AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at, id
            LIMIT #{limit}
            """)
    List<OutboxEvent> findDue(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE outbox_events
            SET status = 'SENT',
                last_error_summary = NULL,
                sent_at = #{sentAt}
            WHERE id = #{eventId}
              AND status IN ('NEW', 'RETRY')
            """)
    int markSent(
            @Param("eventId") Long eventId,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Update("""
            UPDATE outbox_events
            SET status = 'RETRY',
                attempts = attempts + 1,
                next_attempt_at = #{nextAttemptAt},
                last_error_summary = #{errorSummary},
                sent_at = NULL
            WHERE id = #{eventId}
              AND status IN ('NEW', 'RETRY')
            """)
    int markRetry(
            @Param("eventId") Long eventId,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("errorSummary") String errorSummary
    );
}
