-- V1_0_53__create_upload_submissions.sql
-- Purpose: Upload-runtime schema (handed off to a Checker when maker-checker is enabled), ported
-- for referential completeness only — see V1_0_52 header.

CREATE TABLE upload_submissions (
  submission_id                  TEXT PRIMARY KEY DEFAULT generate_id('sub'),
  upload_attempt_id              TEXT NOT NULL UNIQUE REFERENCES upload_attempts (upload_attempt_id) ON DELETE RESTRICT,
  process_id                     TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  process_name                   TEXT NOT NULL,
  template_code                  TEXT NOT NULL,
  template_version                TEXT NOT NULL,
  maker_user_id                   TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  maker_display_name              TEXT NOT NULL,
  pending_object_key              TEXT NOT NULL,
  storage_provider                 VARCHAR(20) NOT NULL CHECK (storage_provider IN ('AWS_S3','AZURE_BLOB','GCS','ON_PREM')),
  summary                          JSONB NOT NULL,
  issues                           JSONB NOT NULL DEFAULT '[]',
  original_file_checksum_sha256   TEXT NOT NULL,
  status                           VARCHAR(20) NOT NULL DEFAULT 'WAITING_FOR_CHECKER'
                                    CHECK (status IN ('WAITING_FOR_CHECKER','ACCEPTED','REJECTED','EXPIRED')),
  checker_user_id                  TEXT REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  review_reason                    TEXT,
  expires_at                       TIMESTAMPTZ NOT NULL,
  created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_submissions_summary_is_object CHECK (jsonb_typeof(summary) = 'object'),
  CONSTRAINT upload_submissions_issues_is_array CHECK (jsonb_typeof(issues) = 'array')
);

CREATE INDEX upload_submissions_maker_idx ON upload_submissions (maker_user_id, created_at DESC);
CREATE INDEX upload_submissions_checker_inbox_idx ON upload_submissions (status) WHERE status = 'WAITING_FOR_CHECKER';

CREATE TRIGGER upload_submissions_set_updated_at
  BEFORE UPDATE ON upload_submissions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN upload_submissions.checker_user_id IS
  'References maker_users because the demo model shares one user table for both maker and checker upload operators (distinct from the Maker/Checker Admin actors).';
COMMENT ON TABLE upload_submissions IS
  'Belongs to the separate upload-operator API (upload-api-contract.md, not yet provided) — schema included for referential completeness only; no service/controller layer in this pass.';
