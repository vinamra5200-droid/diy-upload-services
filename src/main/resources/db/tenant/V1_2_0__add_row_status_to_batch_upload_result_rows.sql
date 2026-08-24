-- V1_2_0__add_row_status_to_batch_upload_result_rows.sql
-- Purpose: every validated row is now pulled and persisted (not just failed ones — see
-- BatchValidationResultServiceImpl / ValidationServiceResultsClient, which now page through
-- validation-service's GET /api/v1/batch-uploads/{batchId}/rows instead of /failed-rows).
-- row_status records each row's outcome. Default 'FAILED' is safe for existing rows: every row
-- persisted before this migration was pulled from the failed-rows-only endpoint, so it really was
-- FAILED.

ALTER TABLE batch_upload_result_rows
    ADD COLUMN row_status VARCHAR(10) NOT NULL DEFAULT 'FAILED';

-- Supports filtering/ordering the results-rows endpoint by status within a batch
CREATE INDEX idx_batch_upload_result_rows_batch_status ON batch_upload_result_rows (batch_id, row_status);
