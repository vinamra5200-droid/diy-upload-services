-- V1_0_57__relax_template_version_snapshots_uniqueness.sql
-- Purpose: The table is documented as "one row per save", but the original UNIQUE(template_id,
-- version) index actually enforced "one row per version" — silently dropping every snapshot after
-- the first at a given version, including the one that should be captured at accept() time (the
-- template's version number does not change between submit and accept, so the accept-time save
-- was always rejected as a duplicate before it could be written). Replace the unique index with a
-- plain one: same query performance for template_id+version lookups, but multiple saves at the
-- same version (e.g. the initial draft, and later the moment it was approved) can each get their
-- own row, as the table's own append-only "one row per save" contract always intended.

DROP INDEX template_version_snapshots_version_uidx;
CREATE INDEX template_version_snapshots_version_idx ON template_version_snapshots (template_id, version);
