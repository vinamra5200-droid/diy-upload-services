-- V1_0_48__create_maker_users.sql
-- Purpose: Batch upload operators — not the Maker Admin actor who owns this table's rows
-- (admin-api-contract.md §4).

CREATE TABLE maker_users (
  user_id           TEXT PRIMARY KEY DEFAULT generate_id('user'),
  username          TEXT NOT NULL,
  full_name         TEXT NOT NULL,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  created_by        TEXT NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT maker_users_username_not_blank CHECK (btrim(username) <> ''),
  CONSTRAINT maker_users_username_len CHECK (char_length(username) <= 120),
  CONSTRAINT maker_users_full_name_not_blank CHECK (btrim(full_name) <> ''),
  CONSTRAINT maker_users_full_name_len CHECK (char_length(full_name) <= 120),
  CONSTRAINT maker_users_rejection_when_rejected CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX maker_users_username_ci_uidx ON maker_users (lower(username));
CREATE INDEX maker_users_status_idx ON maker_users (status);
CREATE INDEX maker_users_inbox_idx ON maker_users (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER maker_users_set_updated_at
  BEFORE UPDATE ON maker_users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
