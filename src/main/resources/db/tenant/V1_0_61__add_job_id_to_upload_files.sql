-- V1_0_61__add_job_id_to_upload_files.sql
-- Purpose: give UploadS3Worker a job id to hand back once the raw S3 PUT completes, so
-- diy-upload-web can key its data-validation-topic Kafka batches on something stable.
--
-- Deliberately NOT the same concept as upload_jobs.job_id (V1_0_54): that table is the
-- post-load-action delivery job for the not-yet-built validation/checker/S3-promote pipeline
-- (JOB_METADATA_CREATED is Solution Design §12.3 event #26, which fires after checker approval
-- and S3 promote — events #20-25) and most of its NOT NULL columns (upload_attempt_id,
-- total_records, completed_file_key, maker_checker_enabled, ...) have no data yet at raw-upload
-- time. This column is a lighter, earlier id scoped to upload_files only; when the real pipeline
-- lands, reconciling the two is a job for that work, not this one.

ALTER TABLE upload_files ADD COLUMN job_id TEXT;

CREATE INDEX upload_files_job_idx ON upload_files (job_id) WHERE job_id IS NOT NULL;

COMMENT ON COLUMN upload_files.job_id IS
  'Set once the S3 PUT completes (UploadS3Worker). Not the same row/concept as upload_jobs.job_id — see this migration''s header.';
