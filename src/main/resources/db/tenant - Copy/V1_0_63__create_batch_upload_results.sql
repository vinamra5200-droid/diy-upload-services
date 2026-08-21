-- V1_0_63__create_batch_upload_results.sql
-- Purpose: local copy of validation-service's per-batch outcome, pulled once when its
-- batch-validation-completed Kafka event arrives (BatchValidationCompletedListener), so
-- diy-upload-web can show row-wise results without ever calling validation-service directly.
-- One batch_upload_results row per batchId (summary/counts); one batch_upload_result_rows row per
-- failed row, mirroring validation-service's own batch_upload_row shape.

CREATE TABLE batch_upload_results (
  batch_id              UUID PRIMARY KEY,
  process_id            TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  template_id           TEXT NOT NULL REFERENCES templates (template_id) ON DELETE RESTRICT,
  status                VARCHAR(20) NOT NULL,
  total_rows_received   INTEGER NOT NULL DEFAULT 0,
  passed_count          INTEGER NOT NULL DEFAULT 0,
  failed_count          INTEGER NOT NULL DEFAULT 0,
  received_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE batch_upload_results IS
  'One row per batch, populated once from validation-service''s completion event and REST '
  'failed-rows pull — not written by any other path.';

CREATE TABLE batch_upload_result_rows (
  id            TEXT PRIMARY KEY DEFAULT generate_id('bres'),
  batch_id      UUID NOT NULL REFERENCES batch_upload_results (batch_id) ON DELETE CASCADE,
  row_number    INTEGER NOT NULL,
  -- raw row data as validation-service returned it, keyed by field name
  row_data      JSONB NOT NULL,
  -- list of {field, ruleType, errorMessage} as validation-service returned it
  errors        JSONB NOT NULL
);

-- Paginated row listing for the results endpoint, in original row order
CREATE INDEX idx_batch_upload_result_rows_batch_row_number ON batch_upload_result_rows (batch_id, row_number);
