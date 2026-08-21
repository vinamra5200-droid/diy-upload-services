-- V1_0_37__create_templates.sql
-- Purpose: Upload templates — column mapping, formats, data-load, post-load action, maker-checker
-- and schedule settings (admin-api-contract.md §2). The source schema's
-- `data_load_primary_key_note` column is a documented-unused placeholder (its comment says so
-- explicitly) and is dropped here; the primary-key field list lives in template_pk_fields
-- (V1_0_40), same as the source.

CREATE TABLE templates (
  template_id                     TEXT PRIMARY KEY DEFAULT generate_id('tmpl'),
  template_code                   TEXT NOT NULL,
  template_name                   TEXT NOT NULL,
  template_description            TEXT NOT NULL DEFAULT '',
  version                         TEXT NOT NULL DEFAULT '1.0.0',
  process_id                      TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  status                          VARCHAR(20) NOT NULL DEFAULT 'draft'
                                   CHECK (status IN ('draft','waitingForChecker','active','rejected')),

  package_max_size_mb             INTEGER NOT NULL DEFAULT 50,
  package_max_rows                INTEGER,

  duplicate_action                VARCHAR(20) NOT NULL DEFAULT 'reject'
                                   CHECK (duplicate_action IN ('reject','skipSilent','overwrite')),
  row_order                       VARCHAR(20) NOT NULL DEFAULT 'inputSequence'
                                   CHECK (row_order IN ('inputSequence','sortByKey')),

  post_load_action_type           VARCHAR(20) NOT NULL DEFAULT 'kafka'
                                   CHECK (post_load_action_type IN ('kafka','database')),
  kafka_topic                     TEXT,
  kafka_bootstrap_servers         TEXT,
  database_mode                   VARCHAR(20) CHECK (database_mode IS NULL OR database_mode IN ('useExisting','custom')),
  database_connection_id          TEXT REFERENCES database_connections (connection_id) ON DELETE RESTRICT,
  database_provider               VARCHAR(20) CHECK (database_provider IS NULL OR database_provider IN ('POSTGRES','MYSQL','SQL_SERVER','ORACLE')),
  database_connection_ref         TEXT,
  database_table_name             TEXT,

  upload_process_timeout_minutes  INTEGER NOT NULL DEFAULT 10,
  validation_worker_threads       INTEGER NOT NULL DEFAULT 10,
  validations_enabled             BOOLEAN NOT NULL DEFAULT TRUE,

  maker_checker_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
  maker_checker_actor_ne_submitter BOOLEAN NOT NULL DEFAULT TRUE,
  maker_checker_sla_hours          INTEGER NOT NULL DEFAULT 24,
  maker_checker_escalate_to_role   TEXT NOT NULL DEFAULT '',

  fail_fast                       BOOLEAN NOT NULL DEFAULT FALSE,

  schedule                        JSONB,

  submitted_by                    TEXT,
  rejection_reason                TEXT,
  created_by                      TEXT NOT NULL,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT templates_code_not_blank CHECK (btrim(template_code) <> ''),
  CONSTRAINT templates_name_not_blank CHECK (btrim(template_name) <> ''),
  CONSTRAINT templates_name_len CHECK (char_length(template_name) <= 120),
  CONSTRAINT templates_package_size_range CHECK (package_max_size_mb BETWEEN 1 AND 500),
  CONSTRAINT templates_package_rows_positive CHECK (package_max_rows IS NULL OR package_max_rows >= 1),
  CONSTRAINT templates_timeout_range CHECK (upload_process_timeout_minutes BETWEEN 1 AND 180),
  CONSTRAINT templates_sla_range CHECK (maker_checker_sla_hours BETWEEN 1 AND 720),
  CONSTRAINT templates_rejection_when_rejected CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> ''),
  CONSTRAINT templates_schedule_is_object CHECK (schedule IS NULL OR jsonb_typeof(schedule) = 'object')
);

CREATE UNIQUE INDEX templates_code_uidx ON templates (template_code);
CREATE INDEX templates_process_idx ON templates (process_id);
CREATE INDEX templates_status_idx ON templates (status);
CREATE INDEX templates_inbox_idx ON templates (status) WHERE status = 'waitingForChecker';
CREATE INDEX templates_database_connection_idx ON templates (database_connection_id);

CREATE TRIGGER templates_set_updated_at
  BEFORE UPDATE ON templates
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN templates.template_code IS 'Server-generated human-readable code (e.g. TPL_A1B2C3D4).';
COMMENT ON COLUMN templates.version IS 'Semver. Patch bumps and status reverts to draft when an active template is edited.';
COMMENT ON COLUMN templates.schedule IS
  'Nullable TemplateScheduleConfig snapshot: {enabled, frequency, timeOfDay, dayOfWeek, dayOfMonth, cronExpression, pickup:{host,port,username,credentialRef,basePath}, filePattern, uploadFormat, autoApprove, lastRunAt, nextRunAt}. pickup.credentialRef is shape-only — never a live secret.';
