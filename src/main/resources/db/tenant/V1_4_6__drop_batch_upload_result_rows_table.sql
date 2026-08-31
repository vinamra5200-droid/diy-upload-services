-- V1_4_6__drop_batch_upload_result_rows_table.sql
-- Purpose: batch_upload_result_rows was a local mirror of every row validation-service holds,
-- populated by BatchValidationResultServiceImpl.recordCompletion pulling the whole batch on
-- completion. That eager pull-everything-even-if-the-maker-never-looks is exactly what's being
-- replaced by on-demand (click-driven, one page at a time) rows browsing straight against
-- validation-service (see UploadAttemptController#getRows / CheckerController#getRows) — nothing
-- populates or reads this table anymore. The CSV export (ValidatedResultS3Exporter) was repointed
-- to stream from validation-service directly rather than this local copy.

DROP TABLE batch_upload_result_rows;
