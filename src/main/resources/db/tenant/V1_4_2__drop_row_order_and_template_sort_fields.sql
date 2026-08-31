-- V1_4_2__drop_row_order_and_template_sort_fields.sql
-- Purpose: dataLoad.rowOrder / sortByKey was persisted config only — nothing in this service ever
-- read it back to actually reorder rows during upload processing, so it was pure unused surface.
-- Drops templates.row_order (V1_0_0) and template_sort_fields (V1_0_0); the corresponding
-- application-level fields/entity/repository/DTOs are removed in the same change.

ALTER TABLE templates DROP COLUMN row_order;

DROP TABLE template_sort_fields;
