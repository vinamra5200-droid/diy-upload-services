-- V1_0_46__create_upload_roles.sql
-- Purpose: Upload roles that gate maker-user access to processes (admin-api-contract.md §3).

CREATE TABLE upload_roles (
  role_id           TEXT PRIMARY KEY DEFAULT generate_id('role'),
  role_name         TEXT NOT NULL,
  description       TEXT NOT NULL DEFAULT '',
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  created_by        TEXT NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_roles_name_not_blank CHECK (btrim(role_name) <> ''),
  CONSTRAINT upload_roles_name_len CHECK (char_length(role_name) <= 64),
  CONSTRAINT upload_roles_description_len CHECK (char_length(description) <= 500),
  CONSTRAINT upload_roles_rejection_when_rejected CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX upload_roles_name_ci_uidx ON upload_roles (lower(role_name));
CREATE INDEX upload_roles_status_idx ON upload_roles (status);
CREATE INDEX upload_roles_inbox_idx ON upload_roles (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER upload_roles_set_updated_at
  BEFORE UPDATE ON upload_roles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
