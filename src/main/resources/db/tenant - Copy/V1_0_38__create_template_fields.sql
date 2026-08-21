-- V1_0_38__create_template_fields.sql
-- Purpose: Column-to-field mapping for a template's upload files (admin-api-contract.md §2.2 fields[]).

CREATE TABLE template_fields (
  field_id        TEXT PRIMARY KEY DEFAULT generate_id('fld'),
  template_id     TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  source_column   TEXT NOT NULL,
  target_field    TEXT NOT NULL,
  field_label     TEXT NOT NULL,
  field_type      VARCHAR(10) NOT NULL DEFAULT 'string' CHECK (field_type IN ('string','number','date','boolean')),
  required        BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order      INTEGER NOT NULL DEFAULT 0,

  CONSTRAINT template_fields_source_not_blank CHECK (btrim(source_column) <> ''),
  CONSTRAINT template_fields_target_not_blank CHECK (btrim(target_field) <> ''),
  CONSTRAINT template_fields_label_not_blank CHECK (btrim(field_label) <> '')
);

CREATE UNIQUE INDEX template_fields_target_uidx ON template_fields (template_id, lower(target_field));
CREATE UNIQUE INDEX template_fields_source_uidx ON template_fields (template_id, lower(source_column));
CREATE INDEX template_fields_template_idx ON template_fields (template_id, sort_order);
