package com.marketplace.shared.internal.audit;

import com.marketplace.shared.Actor;
import com.marketplace.shared.AuditLog;
import com.marketplace.shared.IdGenerator;
import com.marketplace.shared.internal.support.TraceIds;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Inserts audit records with plain SQL in the caller's transaction. */
@Component
public class JdbcAuditLog implements AuditLog {

  private final JdbcClient jdbc;
  private final JsonMapper jsonMapper;
  private final IdGenerator ids;
  private final Clock clock;
  private final TraceIds traceIds;

  JdbcAuditLog(
      JdbcClient jdbc, JsonMapper jsonMapper, IdGenerator ids, Clock clock, TraceIds traceIds) {
    this.jdbc = jdbc;
    this.jsonMapper = jsonMapper;
    this.ids = ids;
    this.clock = clock;
    this.traceIds = traceIds;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      Actor actor,
      String action,
      String targetType,
      String targetId,
      @Nullable Object before,
      @Nullable Object after) {
    jdbc.sql(
            """
            INSERT INTO shared.audit_log
              (id, actor, action, target_type, target_id, before, after, created_at, trace_id)
            VALUES
              (:id, :actor, :action, :targetType, :targetId,
               CAST(:before AS jsonb), CAST(:after AS jsonb), :createdAt, :traceId)
            """)
        .param("id", ids.newId())
        .param("actor", actor.value())
        .param("action", action)
        .param("targetType", targetType)
        .param("targetId", targetId)
        .param("before", toJson(before))
        .param("after", toJson(after))
        .param("createdAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
        .param("traceId", traceIds.current())
        .update();
  }

  private @Nullable String toJson(@Nullable Object value) {
    return value == null ? null : jsonMapper.writeValueAsString(value);
  }
}
