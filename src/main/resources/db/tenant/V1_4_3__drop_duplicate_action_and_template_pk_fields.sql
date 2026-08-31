-- V1_4_3__drop_duplicate_action_and_template_pk_fields.sql
-- Purpose: dataLoad.duplicateAction (reject/skipSilent/overwrite) was persisted config only —
-- nothing in this service ever read it back to actually dedup/upsert rows during upload
-- processing. template_pk_fields existed solely as the dedup/upsert key for that setting (see its
-- own V1_0_0 comment), so it goes in the same change. Drops templates.duplicate_action (V1_0_0)
-- and template_pk_fields (V1_0_0); the corresponding application-level fields/entity/repository/
-- DTOs are removed in the same change.

ALTER TABLE templates DROP COLUMN duplicate_action;

DROP TABLE template_pk_fields;
