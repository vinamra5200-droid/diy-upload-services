-- V1_0_33__create_database_connections.sql
-- Purpose: Database connections — standalone admin resource and the target for a template's
-- Post-Load Action (postLoadAction.databaseMode = "useExisting") (admin-api-contract.md §6).

CREATE TABLE database_connections (
  connection_id     TEXT PRIMARY KEY DEFAULT generate_id('db'),
  provider          VARCHAR(20) NOT NULL CHECK (provider IN ('POSTGRES','MYSQL','SQL_SERVER','ORACLE')),
  connection_label  TEXT NOT NULL,
  connection_ref    TEXT NOT NULL DEFAULT '<set-in-ci>',
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  updated_by        TEXT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT database_connections_label_not_blank CHECK (btrim(connection_label) <> ''),
  CONSTRAINT database_connections_label_len CHECK (char_length(connection_label) <= 120),
  CONSTRAINT database_connections_ref_len CHECK (char_length(connection_ref) <= 500),
  CONSTRAINT database_connections_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX database_connections_label_ci_uidx ON database_connections (lower(connection_label));
CREATE INDEX database_connections_status_idx ON database_connections (status);
CREATE INDEX database_connections_inbox_idx ON database_connections (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER database_connections_set_updated_at
  BEFORE UPDATE ON database_connections
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN database_connections.connection_ref IS
  'Shape-only reference (connection string name / secret alias). Never store live credentials here — resolve via secrets manager at runtime.';
