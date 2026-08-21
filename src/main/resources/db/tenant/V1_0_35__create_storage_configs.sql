-- V1_0_35__create_storage_configs.sql
-- Purpose: Interim object-store connections for pending-submission holding (admin-api-contract.md
-- §5). List+create+edit like storage/database/api-config — not a singleton.

CREATE TABLE storage_configs (
  config_id         TEXT PRIMARY KEY DEFAULT generate_id('stg'),
  provider          VARCHAR(20) NOT NULL CHECK (provider IN ('AWS_S3','AZURE_BLOB','GCS','ON_PREM')),
  connection_label  TEXT NOT NULL,
  connection_ref    TEXT NOT NULL DEFAULT '<set-in-ci>',
  path_pattern      TEXT NOT NULL DEFAULT 'diy-upload/{env}/{process_id}/…',
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  updated_by        TEXT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT storage_configs_label_not_blank CHECK (btrim(connection_label) <> ''),
  CONSTRAINT storage_configs_label_len CHECK (char_length(connection_label) <= 120),
  CONSTRAINT storage_configs_ref_len CHECK (char_length(connection_ref) <= 500),
  CONSTRAINT storage_configs_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX storage_configs_label_ci_uidx ON storage_configs (lower(connection_label));
CREATE INDEX storage_configs_status_idx ON storage_configs (status);
CREATE INDEX storage_configs_inbox_idx ON storage_configs (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER storage_configs_set_updated_at
  BEFORE UPDATE ON storage_configs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN storage_configs.connection_ref IS 'Shape-only reference (ARN / connection name). Never store live secrets here.';
COMMENT ON TABLE storage_configs IS 'Interim object-store connections. List+create+edit like storage/database/api-config — not a singleton.';
