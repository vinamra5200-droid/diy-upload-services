-- V1_0_62__add_config_locked_at_to_processes.sql
-- Purpose: timestamp a process's config_locked flag was set, so a scheduled reaper can force-
-- release a lock that's been held longer than the configured stale-lock timeout (e.g. because
-- validation-service crashed mid-batch and never published its completion event). Nullable and
-- additive — existing rows are unaffected.

ALTER TABLE processes
  ADD COLUMN config_locked_at TIMESTAMPTZ;

COMMENT ON COLUMN processes.config_locked_at IS
  'When config_locked was last set to true. NULL when not locked. Used by the stale-lock reaper '
  '(diy.upload.stale-lock-timeout-minutes) to force-release a lock nothing ever cleared.';
