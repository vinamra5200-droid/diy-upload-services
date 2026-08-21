-- V1_0_54__create_upload_jobs.sql
-- Purpose: Upload-runtime schema (post-load-action delivery job), ported for referential
-- completeness only — see V1_0_52 header.

CREATE TABLE upload_jobs (
  job_id                          TEXT PRIMARY KEY DEFAULT generate_id('job'),
  process_code                    TEXT NOT NULL,
  process_name                    TEXT NOT NULL,
  template_code                   TEXT NOT NULL,
  template_version                 TEXT NOT NULL,
  maker_user_id                    TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  checker_user_id                   TEXT REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  submission_id                     TEXT REFERENCES upload_submissions (submission_id) ON DELETE RESTRICT,
  upload_attempt_id                 TEXT NOT NULL REFERENCES upload_attempts (upload_attempt_id) ON DELETE RESTRICT,
  upload_format                     VARCHAR(10) NOT NULL CHECK (upload_format IN ('xlsx','csv','json')),
  total_records                     INTEGER NOT NULL DEFAULT 0,
  passed_records                    INTEGER NOT NULL DEFAULT 0,
  failed_records                    INTEGER NOT NULL DEFAULT 0,
  warning_records                   INTEGER NOT NULL DEFAULT 0,
  completed_file_key                 TEXT NOT NULL,
  original_object_key                 TEXT,
  storage_provider                    VARCHAR(20) NOT NULL CHECK (storage_provider IN ('AWS_S3','AZURE_BLOB','GCS','ON_PREM')),
  maker_checker_enabled                BOOLEAN NOT NULL,
  original_file_checksum_sha256        TEXT NOT NULL,
  status                                VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED')),
  queue_job_ref                         TEXT,
  created_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX upload_jobs_maker_idx ON upload_jobs (maker_user_id, created_at DESC);
CREATE INDEX upload_jobs_status_idx ON upload_jobs (status);

CREATE TRIGGER upload_jobs_set_updated_at
  BEFORE UPDATE ON upload_jobs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN upload_jobs.queue_job_ref IS
  'Reference into the actual delivery mechanism (Kafka message key, DB-writer batch id) named by the template''s post_load_action — not resolved further here.';
COMMENT ON TABLE upload_jobs IS
  'Belongs to the separate upload-operator API (upload-api-contract.md, not yet provided) — schema included for referential completeness only; no service/controller layer in this pass.';
