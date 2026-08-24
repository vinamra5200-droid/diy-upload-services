-- V1_3_0__upload_operator_pipeline.sql
-- Purpose: upload-api-contract.md (v1.1.0) is now implemented — upload_attempts/upload_submissions/
-- upload_jobs (V1_0_52-54) stop being "referential completeness only" tables. This adds the three
-- columns those tables were missing for the maker/checker flow (S3 object keys for the raw/
-- validated stages, and the Kafka batchId correlating an attempt to its validation-service run)
-- and refreshes their table comments, which had explicitly said "no service/controller layer in
-- this pass."

ALTER TABLE upload_attempts
  ADD COLUMN raw_object_key       TEXT,
  ADD COLUMN validated_object_key TEXT,
  ADD COLUMN batch_id             UUID;

-- One attempt owns at most one validation-service batch; used by BatchValidationResultServiceImpl
-- to resolve which upload_attempts row a batch-validation-completed Kafka event belongs to.
CREATE UNIQUE INDEX upload_attempts_batch_id_uidx ON upload_attempts (batch_id) WHERE batch_id IS NOT NULL;

COMMENT ON TABLE upload_attempts IS
  'One row per file a maker uploads for validation (upload-api-contract.md §2). raw_object_key/validated_object_key track the interim-storage stages (§6); batch_id correlates to batch_upload_results once validation starts.';
COMMENT ON TABLE upload_submissions IS
  'A maker''s attempt handed to a checker for review when the template has maker-checker enabled (upload-api-contract.md §2.4, §4).';
COMMENT ON TABLE upload_jobs IS
  'Post-load-action delivery job, created either directly (maker-checker disabled) or after checker acceptance (upload-api-contract.md §2.4, §4.3).';
