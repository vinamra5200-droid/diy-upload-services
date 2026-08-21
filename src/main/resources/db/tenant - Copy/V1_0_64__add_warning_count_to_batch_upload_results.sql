-- V1_0_64__add_warning_count_to_batch_upload_results.sql
-- Purpose: validation-service now distinguishes ERROR vs WARNING severity per rule failure — this
-- carries the batch-level warning count through the completion event into the local results copy
-- (BatchValidationCompletedListener), alongside the existing passed/failed counts.

ALTER TABLE batch_upload_results
    ADD COLUMN warning_count INTEGER NOT NULL DEFAULT 0;
