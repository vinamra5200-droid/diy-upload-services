-- V1_0_60__extend_audit_events_for_pipeline_trail.sql
-- Purpose: bring audit_events up to the Solution Design §12.4 event schema so it can carry
-- upload-pipeline events (not just admin config mutations) once each pipeline stage is real.
-- All new columns are nullable and additive — every existing ADMIN_* row and call site keeps
-- working unchanged; AuditEventService.record(...) keeps its original 6-arg overload for them.
--
-- Deliberately NOT matching SD §12.4 literally on two points, to stay consistent with this
-- codebase's existing conventions rather than the document's illustrative example:
--   - IDs stay TEXT + generate_id(prefix) (as event_id already is), not UUID.
--   - template_version stays TEXT ("1.0.1"), matching templates/upload_attempts, not an integer.
-- upload_attempt_id/submission_id/job_id are plain TEXT with no FK to upload_attempts/submissions/
-- jobs: those tables exist for referential completeness only (V1_0_52-54) with no service layer
-- yet, so nothing will ever populate them for real until that layer is built.

ALTER TABLE audit_events
  ADD COLUMN trace_id          TEXT,
  ADD COLUMN upload_attempt_id TEXT,
  ADD COLUMN submission_id     TEXT,
  ADD COLUMN job_id            TEXT,
  ADD COLUMN actor_roles       JSONB,
  ADD COLUMN template_version  TEXT,
  ADD COLUMN payload           JSONB,
  ADD COLUMN prev_event_id     TEXT REFERENCES audit_events (event_id),
  ADD CONSTRAINT audit_events_payload_is_object
    CHECK (payload IS NULL OR jsonb_typeof(payload) = 'object'),
  ADD CONSTRAINT audit_events_actor_roles_is_array
    CHECK (actor_roles IS NULL OR jsonb_typeof(actor_roles) = 'array'),
  ADD CONSTRAINT audit_events_event_code_fkey
    FOREIGN KEY (event_code) REFERENCES audit_event_catalogue (event_code);

COMMENT ON COLUMN audit_events.trace_id IS
  'End-to-end correlation id for one maker action (SD §12.2). Caller-generated per action, not '
  'per row — every audit_event emitted while handling that action shares the same trace_id.';
COMMENT ON COLUMN audit_events.prev_event_id IS
  'Previous event in the same upload_attempt_id chain, for tamper-evident ordering (SD §12.4). '
  'NULL for the first event in a chain.';

CREATE INDEX audit_events_trace_idx ON audit_events (trace_id, occurred_at) WHERE trace_id IS NOT NULL;
CREATE INDEX audit_events_attempt_idx ON audit_events (upload_attempt_id, occurred_at) WHERE upload_attempt_id IS NOT NULL;
CREATE INDEX audit_events_job_idx ON audit_events (job_id) WHERE job_id IS NOT NULL;
