-- Level-two audit records (需求 3.6, 設計_004 11). Insert only.
CREATE TABLE shared.audit_log (
  id          uuid        PRIMARY KEY,
  actor       text        NOT NULL,
  action      text        NOT NULL,
  target_type text        NOT NULL,
  target_id   text        NOT NULL,
  before      jsonb,
  after       jsonb,
  created_at  timestamptz NOT NULL,
  trace_id    text
);

CREATE INDEX audit_log_target_idx ON shared.audit_log (target_type, target_id, created_at);
CREATE INDEX audit_log_actor_idx ON shared.audit_log (actor, created_at);
CREATE INDEX audit_log_trace_id_idx ON shared.audit_log (trace_id);
