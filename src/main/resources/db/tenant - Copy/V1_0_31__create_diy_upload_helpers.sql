-- V1_0_31__create_diy_upload_helpers.sql
-- Purpose: Shared helper functions for the DIY Upload Admin feature tables that follow
-- (V1_0_32 onward) — human-readable ID generation and the updated_at touch trigger.
--
-- Ported from the DIY Batch Upload Framework's standalone schema
-- (D:\Source Code\DIY Batch Upload Framework\diy-upload-web\db\migrations\001_init.sql). The
-- original wrapped everything in a `diy_upload` schema with its own search_path; tenant
-- databases use a single `public` schema (AGENTS.md rule 18), so that wrapper is dropped here
-- and every `diy_upload.` prefix is dropped from the tables that follow.

CREATE OR REPLACE FUNCTION generate_id(prefix TEXT)
RETURNS TEXT
LANGUAGE sql
VOLATILE
AS $$
  SELECT prefix || '-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8);
$$;

-- Note: application code (utils/IdGenerator.java) generates IDs in this same format before every
-- INSERT — Hibernate always includes the mapped @Id column, so this DEFAULT only fires for
-- inserts made outside the JPA layer (defensive fallback, matching the source schema).

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
