-- V1_0_39__create_template_upload_formats.sql
-- Purpose: Per-template XLSX/CSV/JSON parse options — exactly one row per format key per
-- template, auto-seeded by a trigger on every templates INSERT (admin-api-contract.md §2.2
-- uploadFormats, §2.3 "seeded uploadFormats").

CREATE TABLE template_upload_formats (
  template_id       TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  format_key        VARCHAR(10) NOT NULL CHECK (format_key IN ('xlsx','csv','json')),
  enabled           BOOLEAN NOT NULL DEFAULT FALSE,
  max_size_mb       INTEGER NOT NULL,
  sheet_name        TEXT,
  delimiter         TEXT,
  charset           TEXT,
  header_row        INTEGER,
  root_array_path   TEXT,

  PRIMARY KEY (template_id, format_key),

  CONSTRAINT template_formats_size_range CHECK (max_size_mb BETWEEN 1 AND 500),
  CONSTRAINT template_formats_header_row_positive CHECK (header_row IS NULL OR header_row >= 1)
);

COMMENT ON TABLE template_upload_formats IS
  'Per-template XLSX / CSV / JSON parse options. At least one format must be enabled (enforced by the service layer, not a table CHECK).';

-- Seed the three format rows whenever a template is created, regardless of insert path — this is
-- an AFTER INSERT trigger on `templates`, so it fires for Hibernate-issued INSERTs too. The
-- service layer re-queries these rows after create() to include them in the response.
CREATE OR REPLACE FUNCTION seed_template_formats()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO template_upload_formats
    (template_id, format_key, enabled, max_size_mb, sheet_name, delimiter, charset, header_row, root_array_path)
  VALUES
    (NEW.template_id, 'xlsx', TRUE,  50, 'Data', NULL, NULL,    NULL, NULL),
    (NEW.template_id, 'csv',  TRUE,  50, NULL,   ',',  'UTF-8', 1,    NULL),
    (NEW.template_id, 'json', FALSE, 30, NULL,   NULL, NULL,    NULL, '$');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER templates_seed_formats
  AFTER INSERT ON templates
  FOR EACH ROW EXECUTE FUNCTION seed_template_formats();
