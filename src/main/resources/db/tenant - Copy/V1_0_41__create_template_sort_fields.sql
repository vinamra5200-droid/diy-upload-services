-- V1_0_41__create_template_sort_fields.sql
-- Purpose: Row sort order used when dataLoad.rowOrder = "sortByKey" (admin-api-contract.md §2.2
-- dataLoad.sortFields).

CREATE TABLE template_sort_fields (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  target_field  TEXT NOT NULL,
  direction     VARCHAR(4) NOT NULL DEFAULT 'asc' CHECK (direction IN ('asc','desc')),
  sort_order    INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (template_id, target_field)
);

CREATE INDEX template_sort_fields_order_idx ON template_sort_fields (template_id, sort_order);
