-- V1_3_2__sequential_process_template_ids.sql
-- Purpose: process_id and template_id were generated as prefix + 8 random hex chars
-- (generate_id(), mirrored in Java by IdGenerator.generate("proc"/"tmpl")) — collision-safe but
-- not orderable or human-readable. Both now come from a dedicated DB sequence instead, so ids
-- sort in creation order (proc-000001, proc-000002, ...). No other generate_id() consumer
-- (fld, R, ver, upl, sub, job, rawupl, bres, evt, role, user, db, stg, apiconfig) is affected —
-- only the two top-level config entities.
--
-- Sequences are bumped past whatever numeric suffix already exists (covers the 'proc-000001' /
-- 'tmpl-000001' seed rows from V1_1_0, renumbered in the same change) so the next app-generated
-- id never collides with seeded data on a tenant DB that already ran the earlier migrations.

CREATE SEQUENCE process_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE template_id_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION generate_sequential_id(prefix TEXT, seq_name TEXT)
RETURNS TEXT
LANGUAGE sql
VOLATILE
AS $$
  SELECT prefix || '-' || lpad(nextval(seq_name::regclass)::text, 6, '0');
$$;

SELECT setval('process_id_seq',
  coalesce((SELECT max(substring(process_id from 'proc-([0-9]+)$')::bigint) FROM processes), 0),
  true);
SELECT setval('template_id_seq',
  coalesce((SELECT max(substring(template_id from 'tmpl-([0-9]+)$')::bigint) FROM templates), 0),
  true);

ALTER TABLE processes ALTER COLUMN process_id SET DEFAULT generate_sequential_id('proc', 'process_id_seq');
ALTER TABLE templates ALTER COLUMN template_id SET DEFAULT generate_sequential_id('tmpl', 'template_id_seq');

-- Note: application code (utils/IdGenerator.java's fromSequence(), called from
-- ProcessServiceImpl/TemplateServiceImpl via the repositories' nextProcessIdSequence() /
-- nextTemplateIdSequence()) pulls nextval() from these same sequences before every INSERT —
-- Hibernate always includes the mapped @Id column, so this DEFAULT only fires for inserts made
-- outside the JPA layer (defensive fallback, matching V1_0_31's generate_id() convention).
