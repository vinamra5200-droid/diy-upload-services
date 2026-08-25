-- V1_1_0__seed_vendor_onboarding_demo.sql
-- Purpose: Seed a ready-to-use "Vendor Onboarding" process + template into every tenant database,
-- demonstrating the full template config schema end to end (same pattern as V1_1_30's
-- example_entity seed — reference data every tenant gets, not tenant-specific business data):
--   * 10 template_fields (column mappings), covering string/number/date/boolean field_types
--   * 1 template_pk_fields row (dedup/upsert key)
--   * 8 template_validation_rules, one of each non-reserved rule_type (MASTER_DATA is excluded —
--     it's reserved/coming-soon and blocks a template from ever reaching 'active', per
--     TemplateServiceImpl#assertNoMasterDataRules)
--   * 3 template_transformations (value recode mappings), one per transformable field
-- QCP versioning: V1_1_x = insert/seed data (DML), patch 0 = vendor onboarding demo
--
-- process/template are seeded directly as 'active' — writing straight to the tables bypasses the
-- maker-checker submit/accept workflow (which only exists in the service layer), so the seeded
-- data is immediately usable without a separate activation step.
--
-- Once provisioned, upload a file with headers matching the source_column values below against:
--   POST /api/v1/uploads/proc-000001/tmpl-000001
--
-- process_id/template_id are literal here (not generate_id()/generate_sequential_id()) because
-- Flyway inserts run outside the JPA layer — 'proc-000001'/'tmpl-000001' are chosen to match what
-- V1_3_2's process_id_seq/template_id_seq would hand out as the first values, so this seed data
-- and app-created rows share one continuous, gap-free numbering.

-- ---- Process ----
INSERT INTO processes
  (process_id, process_name, description, status, validations_enabled, created_by)
VALUES
  ('proc-000001', 'Vendor Onboarding (seed demo)',
   'Demo process seeded by V1_1_0 — bulk vendor master creation.',
   'active', TRUE, 'seed_script');

-- ---- Template ----
-- template_upload_formats is auto-seeded by the templates_seed_formats trigger (xlsx + csv
-- enabled, json disabled, per V1_0_0's template_upload_formats section) — no manual insert here.
INSERT INTO templates
  (template_id, template_code, template_name, template_description, process_id, status,
   duplicate_action, row_order,
   post_load_action_type, kafka_topic, kafka_bootstrap_servers,
   validations_enabled, maker_checker_enabled, created_by)
VALUES
  ('tmpl-000001', 'TPL_VENDOR_ONBOARDING_SEED', 'Vendor Master Upload (seed demo)',
   'Demo template seeded by V1_1_0.', 'proc-000001',
   'active',
   'overwrite', 'inputSequence',
   'kafka', 'vendor-onboarding-data-load', 'localhost:9092',
   TRUE, FALSE, 'seed_script');

-- ---- Field mappings (10) — source_column is the expected upload file header ----
INSERT INTO template_fields
  (template_id, source_column, target_field, field_label, field_type, required, sort_order)
VALUES
  ('tmpl-000001', 'Vendor Code',            'vendor_code',      'Vendor Code',            'string',  TRUE,  0),
  ('tmpl-000001', 'Vendor Name',             'vendor_name',      'Vendor Name',            'string',  TRUE,  1),
  ('tmpl-000001', 'Email Address',           'email',            'Email Address',          'string',  TRUE,  2),
  ('tmpl-000001', 'Phone Number',            'phone_number',     'Phone Number',           'string',  TRUE,  3),
  ('tmpl-000001', 'GST Number',              'gst_number',       'GST Number',             'string',  FALSE, 4),
  ('tmpl-000001', 'PAN Number',              'pan_number',       'PAN Number',             'string',  TRUE,  5),
  ('tmpl-000001', 'State',                   'state',            'State',                  'string',  FALSE, 6),
  ('tmpl-000001', 'Annual Turnover (INR)',   'annual_turnover',  'Annual Turnover (INR)',  'number',  FALSE, 7),
  ('tmpl-000001', 'Onboarding Date',         'onboarding_date',  'Onboarding Date',        'date',    FALSE, 8),
  ('tmpl-000001', 'GST Registered (Y/N)',    'gst_registered',   'GST Registered',         'boolean', FALSE, 9);

-- ---- Primary key field (dedup/upsert target for duplicate_action = 'overwrite') ----
INSERT INTO template_pk_fields
  (template_id, target_field, sort_order)
VALUES
  ('tmpl-000001', 'vendor_code', 0);

-- ---- Validation rules — one of each non-reserved rule_type ----
INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, pattern, sort_order)
VALUES
  ('tmpl-000001', 'gst_number', 'FORMAT_REGEX', 'ERROR',
   'GST number must match the standard 15-character GSTIN format',
   '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$', 0);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, required, reject_empty_string, reject_whitespace, sort_order)
VALUES
  ('tmpl-000001', 'vendor_name', 'NULL_EMPTY', 'ERROR',
   'Vendor Name is mandatory and cannot be blank', TRUE, TRUE, TRUE, 1);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, allowed_values, case_insensitive, sort_order)
VALUES
  ('tmpl-000001', 'state', 'ENUM', 'ERROR',
   'State must be one of the approved states',
   ARRAY['Maharashtra','Karnataka','Delhi','Tamil Nadu','Gujarat'], TRUE, 2);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, decimal_places, delimiter, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'DECIMAL_PRECISION', 'WARNING',
   'Annual turnover should not carry more than 2 decimal places', 2, '.', 3);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, min_value, max_value, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'RANGE', 'ERROR',
   'Annual turnover must be between 0 and 10,000,000,000', 0, 10000000000, 4);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, format, sort_order)
VALUES
  ('tmpl-000001', 'onboarding_date', 'DATE_FORMAT', 'ERROR',
   'Onboarding Date must be in yyyy-MM-dd format', 'yyyy-MM-dd', 5);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, expression, formula_terms, formula_operators, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'FUNCTIONAL', 'ERROR',
   'Annual turnover must not be negative',
   'annual_turnover - 0 >= 0',
   '[{"kind":"field","field":"annual_turnover"},{"kind":"constant","value":0}]'::jsonb,
   '["subtract"]'::jsonb, 6);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, compare_operator, group_by_field, transaction_split, condition, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'TRANSACTION', 'WARNING',
   'Grouped turnover for Maharashtra vendors should not exceed Karnataka vendors, among GST-registered rows',
   'lte', 'vendor_code',
   '{"splitField":"state","branchAValue":"Maharashtra","branchBValue":"Karnataka","amountField":"annual_turnover"}'::jsonb,
   '{"conditionField":"gst_registered","conditionOperator":"equals","conditionValue":"true"}'::jsonb, 7);

-- ---- Transformations — value recode mappings, one per transformable field ----
INSERT INTO template_transformations
  (template_id, target_field, mappings, sort_order)
VALUES
  ('tmpl-000001', 'state',
   '[{"from":"MH","to":"Maharashtra"},{"from":"KA","to":"Karnataka"},{"from":"DL","to":"Delhi"},{"from":"TN","to":"Tamil Nadu"},{"from":"GJ","to":"Gujarat"}]'::jsonb,
   0),
  ('tmpl-000001', 'gst_registered',
   '[{"from":"Y","to":"true"},{"from":"N","to":"false"}]'::jsonb,
   1),
  ('tmpl-000001', 'phone_number',
   '[{"from":"0","to":"+91"}]'::jsonb,
   2);
