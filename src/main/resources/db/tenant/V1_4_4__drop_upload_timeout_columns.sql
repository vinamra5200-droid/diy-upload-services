-- V1_4_4__drop_upload_timeout_columns.sql
-- Purpose: templates.upload_process_timeout_minutes (a per-template, 1-180 minute cap) and
-- upload_attempts.timeout_minutes (that value frozen onto each attempt at creation) are replaced
-- by a single fixed platform-wide qcp.upload.attempt-timeout-minutes setting (see
-- UploadPipelineReaper), not a per-template one. Drops templates.upload_process_timeout_minutes
-- (V1_0_0) and upload_attempts.timeout_minutes (V1_0_0); the corresponding application-level
-- fields/entity/DTOs are removed in the same change.

ALTER TABLE templates DROP COLUMN upload_process_timeout_minutes;

ALTER TABLE upload_attempts DROP COLUMN timeout_minutes;
