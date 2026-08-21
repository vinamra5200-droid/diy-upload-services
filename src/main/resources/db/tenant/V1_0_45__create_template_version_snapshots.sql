-- V1_0_45__create_template_version_snapshots.sql
-- Purpose: Immutable, append-only template snapshots — one row per save (admin-api-contract.md §2.8/§2.9).

CREATE TABLE template_version_snapshots (
  snapshot_id   TEXT PRIMARY KEY DEFAULT generate_id('ver'),
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  version       TEXT NOT NULL,
  snapshot      JSONB NOT NULL,
  captured_by   TEXT NOT NULL,
  captured_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT template_version_snapshots_is_object CHECK (jsonb_typeof(snapshot) = 'object')
);

CREATE INDEX template_version_snapshots_template_idx ON template_version_snapshots (template_id, captured_at DESC);
CREATE UNIQUE INDEX template_version_snapshots_version_uidx ON template_version_snapshots (template_id, version);

COMMENT ON TABLE template_version_snapshots IS
  'Append-only, one row per save: a full point-in-time copy of the template (fields, rules, formats, transformations, post-load action, schedule, etc.). Never UPDATE or DELETE.';

REVOKE UPDATE, DELETE ON template_version_snapshots FROM PUBLIC;
