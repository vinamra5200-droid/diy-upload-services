-- seed-vendor-onboarding-demo.sql
-- Purpose: Manual demo/test seed — a "Vendor Onboarding" process + template, ready to accept
-- uploads, exercising every corner of the template config schema:
--   * 10 template_fields (column mappings), covering string/number/date field_types
--   * 1 template_pk_fields row (dedup/upsert key)
--   * 8 template_validation_rules, one of each non-reserved rule_type (MASTER_DATA is excluded —
--     it's reserved/coming-soon and blocks a template from ever reaching 'active', per
--     TemplateServiceImpl#assertNoMasterDataRules)
--   * 3 template_transformations (value recode mappings), one per transformable field
--
-- Not a Flyway migration — run manually, after the full schema (db/tenant/*.sql) is in place,
-- against a tenant database:
--   psql "postgresql://<user>:<pass>@localhost:5432/mt-template-qc-db" -f seed-vendor-onboarding-demo.sql
--
-- Idempotent: safe to re-run. The cleanup block below deletes any previous run's rows by their
-- fixed IDs before inserting fresh ones (template_fields/pk_fields/validation_rules/
-- transformations/upload_formats all cascade off templates.template_id).
--
-- process/template are seeded directly as 'active' — this script writes straight to the tables,
-- bypassing the maker-checker submit/accept workflow (which only exists in the service layer),
-- so the seeded data is immediately usable without a separate activation step.
--
-- Once seeded, upload a file with headers matching the source_column values below against:
--   POST /api/v1/uploads/proc-seed-vendor-onboarding/tmpl-seed-vendor-onboarding

BEGIN;

-- ---- Cleanup (idempotent re-run) ----
DELETE FROM templates WHERE template_id = 'tmpl-seed-vendor-onboarding';
DELETE FROM processes WHERE process_id = 'proc-seed-vendor-onboarding';

-- ---- Process ----
INSERT INTO processes
  (process_id, process_name, description, status, validations_enabled, created_by)
VALUES
  ('proc-seed-vendor-onboarding', 'Vendor Onboarding (seed demo)',
   'Demo process seeded by script/seed-vendor-onboarding-demo.sql — bulk vendor master creation.',
   'active', TRUE, 'seed_script');

-- ---- Template ----
-- template_upload_formats is auto-seeded by the templates_seed_formats trigger (xlsx + csv
-- enabled, json disabled, per V1_0_39) — no manual insert needed there.
INSERT INTO templates
  (template_id, template_code, template_name, template_description, process_id, status,
   duplicate_action, row_order,
   post_load_action_type, kafka_topic, kafka_bootstrap_servers,
   validations_enabled, maker_checker_enabled, created_by)
VALUES
  ('tmpl-seed-vendor-onboarding', 'TPL_VENDOR_ONBOARDING_SEED', 'Vendor Master Upload (seed demo)',
   'Demo template seeded by script/seed-vendor-onboarding-demo.sql.', 'proc-seed-vendor-onboarding',
   'active',
   'overwrite', 'inputSequence',
   'kafka', 'vendor-onboarding-data-load', 'localhost:9092',
   TRUE, FALSE, 'seed_script');

-- ---- Field mappings (10) — source_column is the expected upload file header ----
INSERT INTO template_fields
  (template_id, source_column, target_field, field_label, field_type, required, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'Vendor Code',            'vendor_code',      'Vendor Code',            'string',  TRUE,  0),
  ('tmpl-seed-vendor-onboarding', 'Vendor Name',             'vendor_name',      'Vendor Name',            'string',  TRUE,  1),
  ('tmpl-seed-vendor-onboarding', 'Email Address',           'email',            'Email Address',          'string',  TRUE,  2),
  ('tmpl-seed-vendor-onboarding', 'Phone Number',            'phone_number',     'Phone Number',           'string',  TRUE,  3),
  ('tmpl-seed-vendor-onboarding', 'GST Number',              'gst_number',       'GST Number',             'string',  FALSE, 4),
  ('tmpl-seed-vendor-onboarding', 'PAN Number',              'pan_number',       'PAN Number',             'string',  TRUE,  5),
  ('tmpl-seed-vendor-onboarding', 'State',                   'state',            'State',                  'string',  FALSE, 6),
  ('tmpl-seed-vendor-onboarding', 'Annual Turnover (INR)',   'annual_turnover',  'Annual Turnover (INR)',  'number',  FALSE, 7),
  ('tmpl-seed-vendor-onboarding', 'Onboarding Date',         'onboarding_date',  'Onboarding Date',        'date',    FALSE, 8),
  ('tmpl-seed-vendor-onboarding', 'GST Registered (Y/N)',    'gst_registered',   'GST Registered',         'boolean', FALSE, 9);

-- ---- Primary key field (dedup/upsert target for duplicate_action = 'overwrite') ----
INSERT INTO template_pk_fields
  (template_id, target_field, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'vendor_code', 0);

-- ---- Validation rules — one of each non-reserved rule_type ----
INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, pattern, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'gst_number', 'FORMAT_REGEX', 'ERROR',
   'GST number must match the standard 15-character GSTIN format',
   '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$', 0);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, required, reject_empty_string, reject_whitespace, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'vendor_name', 'NULL_EMPTY', 'ERROR',
   'Vendor Name is mandatory and cannot be blank', TRUE, TRUE, TRUE, 1);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, allowed_values, case_insensitive, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'state', 'ENUM', 'ERROR',
   'State must be one of the approved states',
   ARRAY['Maharashtra','Karnataka','Delhi','Tamil Nadu','Gujarat'], TRUE, 2);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, decimal_places, delimiter, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'annual_turnover', 'DECIMAL_PRECISION', 'WARNING',
   'Annual turnover should not carry more than 2 decimal places', 2, '.', 3);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, min_value, max_value, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'annual_turnover', 'RANGE', 'ERROR',
   'Annual turnover must be between 0 and 10,000,000,000', 0, 10000000000, 4);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, format, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'onboarding_date', 'DATE_FORMAT', 'ERROR',
   'Onboarding Date must be in yyyy-MM-dd format', 'yyyy-MM-dd', 5);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, expression, formula_terms, formula_operators, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'annual_turnover', 'FUNCTIONAL', 'ERROR',
   'Annual turnover must not be negative',
   'annual_turnover - 0 >= 0',
   '[{"kind":"field","field":"annual_turnover"},{"kind":"constant","value":0}]'::jsonb,
   '["subtract"]'::jsonb, 6);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, compare_operator, group_by_field, transaction_split, condition, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'annual_turnover', 'TRANSACTION', 'WARNING',
   'Grouped turnover for Maharashtra vendors should not exceed Karnataka vendors, among GST-registered rows',
   'lte', 'vendor_code',
   '{"splitField":"state","branchAValue":"Maharashtra","branchBValue":"Karnataka","amountField":"annual_turnover"}'::jsonb,
   '{"conditionField":"gst_registered","conditionOperator":"equals","conditionValue":"true"}'::jsonb, 7);

-- ---- Transformations — value recode mappings, one per transformable field ----
INSERT INTO template_transformations
  (template_id, target_field, mappings, sort_order)
VALUES
  ('tmpl-seed-vendor-onboarding', 'state',
   '[{"from":"MH","to":"Maharashtra"},{"from":"KA","to":"Karnataka"},{"from":"DL","to":"Delhi"},{"from":"TN","to":"Tamil Nadu"},{"from":"GJ","to":"Gujarat"}]'::jsonb,
   0),
  ('tmpl-seed-vendor-onboarding', 'gst_registered',
   '[{"from":"Y","to":"true"},{"from":"N","to":"false"}]'::jsonb,
   1),
  ('tmpl-seed-vendor-onboarding', 'phone_number',
   '[{"from":"0","to":"+91"}]'::jsonb,
   2);

COMMIT;

-- ---- Verification ----
SELECT 'process' AS what, count(*) FROM processes WHERE process_id = 'proc-seed-vendor-onboarding'
UNION ALL
SELECT 'template', count(*) FROM templates WHERE template_id = 'tmpl-seed-vendor-onboarding'
UNION ALL
SELECT 'upload_formats (auto-seeded)', count(*) FROM template_upload_formats WHERE template_id = 'tmpl-seed-vendor-onboarding'
UNION ALL
SELECT 'fields', count(*) FROM template_fields WHERE template_id = 'tmpl-seed-vendor-onboarding'
UNION ALL
SELECT 'pk_fields', count(*) FROM template_pk_fields WHERE template_id = 'tmpl-seed-vendor-onboarding'
UNION ALL
SELECT 'validation_rules', count(*) FROM template_validation_rules WHERE template_id = 'tmpl-seed-vendor-onboarding'
UNION ALL
SELECT 'transformations', count(*) FROM template_transformations WHERE template_id = 'tmpl-seed-vendor-onboarding';
