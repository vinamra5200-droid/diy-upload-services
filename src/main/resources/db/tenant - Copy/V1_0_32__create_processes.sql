-- V1_0_32__create_processes.sql
-- Purpose: Upload process definitions. Maker drafts; Checker activates
-- (admin-api-contract.md §1). Postgres native enums from the source schema become
-- VARCHAR + CHECK here (see V1_0_31 header) so they map to @Enumerated(EnumType.STRING) with no
-- custom Hibernate type.

CREATE TABLE processes (
  process_id                TEXT PRIMARY KEY DEFAULT generate_id('proc'),
  process_name              TEXT NOT NULL,
  description               TEXT NOT NULL DEFAULT '',
  status                    VARCHAR(20) NOT NULL DEFAULT 'draft'
                             CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  validations_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  validations_skip_reason   TEXT,
  config_locked             BOOLEAN NOT NULL DEFAULT FALSE,
  config_lock_ref           TEXT,
  submitted_by              TEXT,
  rejection_reason          TEXT,
  created_by                TEXT NOT NULL,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT processes_name_not_blank CHECK (btrim(process_name) <> ''),
  CONSTRAINT processes_name_len CHECK (char_length(process_name) <= 120),
  CONSTRAINT processes_description_len CHECK (char_length(description) <= 500),
  CONSTRAINT processes_skip_reason_when_disabled
    CHECK (validations_enabled OR btrim(coalesce(validations_skip_reason, '')) <> ''),
  CONSTRAINT processes_lock_ref_when_locked
    CHECK (NOT config_locked OR btrim(coalesce(config_lock_ref, '')) <> ''),
  CONSTRAINT processes_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

-- Case-insensitive uniqueness on process name (create/update conflict checks)
CREATE UNIQUE INDEX processes_name_ci_uidx ON processes (lower(process_name));
-- Status filter (list endpoint's ?status= query param)
CREATE INDEX processes_status_idx ON processes (status);
-- Checker inbox lookup
CREATE INDEX processes_inbox_idx ON processes (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER processes_set_updated_at
  BEFORE UPDATE ON processes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE processes IS 'Upload process definitions. Maker drafts; Checker activates.';
