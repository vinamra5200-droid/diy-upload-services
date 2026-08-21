-- V1_0_56__create_upload_files.sql
-- Purpose: Tracks each raw file upload to the interim object store (MakerUploadController /
-- S3UploadServiceImpl). checksum_sha256 lets a repeat upload of the same file for the same
-- template be rejected before it reaches S3; status lets callers report pending/inProgress/
-- completed/failed counts. Distinct from upload_attempts (V1_0_52), which is the not-yet-built
-- validation-pipeline table from upload-api-contract.md — this one tracks the storage step only.

CREATE TABLE upload_files (
  upload_id          TEXT PRIMARY KEY DEFAULT generate_id('rawupl'),
  process_id         TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  template_id        TEXT NOT NULL REFERENCES templates (template_id) ON DELETE RESTRICT,
  original_filename  TEXT NOT NULL,
  checksum_sha256    TEXT NOT NULL,
  file_size_bytes    BIGINT NOT NULL,
  content_type       TEXT,
  s3_bucket          TEXT,
  s3_key             TEXT,
  e_tag              TEXT,
  status             VARCHAR(12) NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending','inProgress','completed','failed')),
  uploaded_by        TEXT NOT NULL,
  error_message      TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_files_filename_not_blank CHECK (btrim(original_filename) <> ''),
  CONSTRAINT upload_files_checksum_not_blank CHECK (btrim(checksum_sha256) <> '')
);

-- Application-level duplicate check races two concurrent uploads of the same file; this is the
-- DB-level backstop — at most one non-failed row per (template, checksum).
CREATE UNIQUE INDEX upload_files_dedup_uidx ON upload_files (template_id, checksum_sha256)
  WHERE status <> 'failed';

CREATE INDEX upload_files_status_idx ON upload_files (status);
CREATE INDEX upload_files_process_idx ON upload_files (process_id);
CREATE INDEX upload_files_template_idx ON upload_files (template_id);

CREATE TRIGGER upload_files_set_updated_at
  BEFORE UPDATE ON upload_files
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE upload_files IS
  'One row per raw file upload attempt to the interim object store. checksum_sha256 blocks duplicate uploads of the same file for the same template (see upload_files_dedup_uidx); status tracks pending/inProgress/completed/failed for count reporting.';
