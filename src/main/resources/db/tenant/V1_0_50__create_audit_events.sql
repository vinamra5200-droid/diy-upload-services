-- V1_0_50__create_audit_events.sql
-- Purpose: Append-only admin activity log (admin-api-contract.md §9). Every state mutation across
-- every resource appends a row here via AuditEventService.

CREATE TABLE audit_events (
  event_id        TEXT PRIMARY KEY DEFAULT generate_id('evt'),
  event_code      TEXT NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_id        TEXT NOT NULL,
  process_id      TEXT,
  template_code   TEXT,
  outcome         VARCHAR(10) NOT NULL CHECK (outcome IN ('SUCCESS','FAILURE','INFO')),
  summary         TEXT NOT NULL,

  CONSTRAINT audit_events_code_not_blank CHECK (btrim(event_code) <> ''),
  CONSTRAINT audit_events_summary_not_blank CHECK (btrim(summary) <> '')
);

CREATE INDEX audit_events_occurred_idx ON audit_events (occurred_at DESC);
CREATE INDEX audit_events_actor_idx ON audit_events (actor_id);
CREATE INDEX audit_events_process_idx ON audit_events (process_id);
CREATE INDEX audit_events_code_idx ON audit_events (event_code);
CREATE INDEX audit_events_outcome_idx ON audit_events (outcome);

COMMENT ON TABLE audit_events IS 'Append-only admin activity log. Do not UPDATE or DELETE rows.';

REVOKE UPDATE, DELETE ON audit_events FROM PUBLIC;
