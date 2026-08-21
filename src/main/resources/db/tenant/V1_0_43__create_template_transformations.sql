-- V1_0_43__create_template_transformations.sql
-- Purpose: Field value transformations (recode mappings applied at load time)
-- (admin-api-contract.md §2.2 transformations[]).

CREATE TABLE template_transformations (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  target_field  TEXT NOT NULL,
  mappings      JSONB NOT NULL DEFAULT '[]',
  sort_order    INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (template_id, target_field),

  CONSTRAINT template_transformations_mappings_is_array CHECK (jsonb_typeof(mappings) = 'array')
);

COMMENT ON COLUMN template_transformations.mappings IS
  'Array of {from, to} value-recode pairs applied to target_field during load.';
