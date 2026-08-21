-- V1_0_36__create_api_configs.sql
-- Purpose: Reusable outbound HTTP call definitions (admin-api-contract.md §7).

CREATE TABLE api_configs (
  config_id         TEXT PRIMARY KEY DEFAULT generate_id('apiconfig'),
  label             TEXT NOT NULL,
  method            VARCHAR(10) NOT NULL DEFAULT 'GET' CHECK (method IN ('GET','POST','PUT','PATCH','DELETE')),
  uri               TEXT NOT NULL,
  query_params      JSONB NOT NULL DEFAULT '[]',
  headers           JSONB NOT NULL DEFAULT '[]',
  body              TEXT NOT NULL DEFAULT '',
  auth              JSONB NOT NULL DEFAULT '{"type":"none","username":"","password":"","token":"","apiKeyName":"","apiKeyValue":"","apiKeyLocation":"header"}',
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  updated_by        TEXT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT api_configs_label_not_blank CHECK (btrim(label) <> ''),
  CONSTRAINT api_configs_uri_not_blank CHECK (btrim(uri) <> ''),
  CONSTRAINT api_configs_query_params_is_array CHECK (jsonb_typeof(query_params) = 'array'),
  CONSTRAINT api_configs_headers_is_array CHECK (jsonb_typeof(headers) = 'array'),
  CONSTRAINT api_configs_auth_is_object CHECK (jsonb_typeof(auth) = 'object'),
  CONSTRAINT api_configs_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX api_configs_label_ci_uidx ON api_configs (lower(label));
CREATE INDEX api_configs_status_idx ON api_configs (status);
CREATE INDEX api_configs_inbox_idx ON api_configs (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER api_configs_set_updated_at
  BEFORE UPDATE ON api_configs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN api_configs.auth IS
  'Shape-only credential fields. Never store live secrets here — resolve via secrets manager at runtime. Shape: {type, username, password, token, apiKeyName, apiKeyValue, apiKeyLocation}.';
COMMENT ON COLUMN api_configs.query_params IS 'Array of {key, value} pairs.';
COMMENT ON COLUMN api_configs.headers IS 'Array of {key, value} pairs.';
