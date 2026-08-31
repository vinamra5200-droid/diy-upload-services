-- V1_4_7__add_pre_staged_processing_key_to_submissions.sql
-- Purpose: When a maker submits to a checker, the file is now also copied into the
-- pending_processing S3 stage immediately (not just pending_approval), so an UploadJob can be
-- created at checker-accept time without a second S3 copy. This column tracks that pre-staged
-- key; it is cleared (object deleted) on rejection so nothing is left orphaned in that stage.

ALTER TABLE upload_submissions ADD COLUMN pre_staged_processing_object_key TEXT;
