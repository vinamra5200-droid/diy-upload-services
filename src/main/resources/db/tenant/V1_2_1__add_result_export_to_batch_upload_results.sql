-- V1_2_1__add_result_export_to_batch_upload_results.sql
-- Purpose: once ValidatedResultS3Exporter writes the row-by-row CSV to S3 (diy-upload/{env}/
-- {processId}/{templateId}/validated/), its bucket/key are recorded here so the maker UI can
-- offer a "download validated file" link. Nullable — populated only after a successful export;
-- a failed export leaves these null without affecting the already-persisted row results.

ALTER TABLE batch_upload_results
    ADD COLUMN result_s3_bucket TEXT,
    ADD COLUMN result_s3_key TEXT;
