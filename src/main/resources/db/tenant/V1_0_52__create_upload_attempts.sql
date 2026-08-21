-- V1_0_52__create_upload_attempts.sql
-- Purpose: Upload-runtime schema (one row per file a maker uploads for validation), ported for
-- referential completeness only. Belongs to the separate upload-operator API
-- (upload-api-contract.md), which has not been provided yet — no Java entity/service/controller
-- layer is built for this table in this pass.

CREATE TABLE upload_attempts (
  upload_attempt_id             TEXT PRIMARY KEY DEFAULT generate_id('upl'),
  process_id                    TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  process_name                  TEXT NOT NULL,
  template_id                   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE RESTRICT,
  template_code                 TEXT NOT NULL,
  template_version               TEXT NOT NULL,
  maker_user_id                  TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  original_filename              TEXT NOT NULL,
  upload_format                  VARCHAR(10) NOT NULL CHECK (upload_format IN ('xlsx','csv','json')),
  file_size_bytes                BIGINT NOT NULL,
  original_file_checksum_sha256  TEXT NOT NULL,
  status                         VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED' CHECK (status IN
                                  ('ACCEPTED','VALIDATING','READY_FOR_DECISION','CONTINUED','REUPLOADED','TIMED_OUT','ABORTED')),
  summary                        JSONB,
  issues                         JSONB NOT NULL DEFAULT '[]',
  decision                       VARCHAR(10) CHECK (decision IS NULL OR decision IN ('PROCEED','REUPLOAD')),
  decided_at                     TIMESTAMPTZ,
  timeout_minutes                INTEGER NOT NULL,
  maker_checker_enabled          BOOLEAN NOT NULL,
  validations_enabled            BOOLEAN NOT NULL,
  created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_attempts_summary_is_object CHECK (summary IS NULL OR jsonb_typeof(summary) = 'object'),
  CONSTRAINT upload_attempts_issues_is_array CHECK (jsonb_typeof(issues) = 'array')
);

CREATE INDEX upload_attempts_process_idx ON upload_attempts (process_id);
CREATE INDEX upload_attempts_maker_idx ON upload_attempts (maker_user_id, created_at DESC);
CREATE INDEX upload_attempts_status_idx ON upload_attempts (status);

-- Enforce "one active (non-terminal) attempt per process" at the DB level too.
CREATE UNIQUE INDEX upload_attempts_one_active_per_process_uidx ON upload_attempts (process_id)
  WHERE status NOT IN ('CONTINUED', 'REUPLOADED', 'TIMED_OUT', 'ABORTED');

CREATE TRIGGER upload_attempts_set_updated_at
  BEFORE UPDATE ON upload_attempts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE upload_attempts IS
  'Belongs to the separate upload-operator API (upload-api-contract.md, not yet provided) — schema included for referential completeness only; no service/controller layer in this pass.';
