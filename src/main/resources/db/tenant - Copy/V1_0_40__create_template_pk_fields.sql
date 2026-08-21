-- V1_0_40__create_template_pk_fields.sql
-- Purpose: Ordered data-load primary/composite key field list (admin-api-contract.md §2.2
-- dataLoad.primaryKeyFields).

CREATE TABLE template_pk_fields (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  target_field  TEXT NOT NULL,
  sort_order    INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (template_id, target_field)
);

CREATE INDEX template_pk_fields_order_idx ON template_pk_fields (template_id, sort_order);
