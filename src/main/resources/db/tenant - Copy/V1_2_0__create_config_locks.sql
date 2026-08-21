-- V1_2_0__create_config_locks.sql
-- Purpose: Replace the single-holder processes.config_locked/config_lock_ref/config_locked_at
-- columns with a proper multi-holder lock table. S3UploadServiceImpl acquires one lock row per
-- upload, and a process can have several uploads in flight for it at once — the process must stay
-- locked against maker-admin config edits (TemplateServiceImpl/ProcessServiceImpl) for as long as
-- ANY of them holds a row, not just whichever one most recently acquired. A single ref column
-- can't represent that: an earlier upload finishing first would wrongly clear the lock while a
-- later one is still running. lock_ref is the uploadId (later reassigned to the Kafka batchId,
-- see ConfigLockService#reassignRef) — globally unique via IdGenerator/UUID, hence the PK here.

CREATE TABLE config_locks (
  process_id  TEXT NOT NULL REFERENCES processes (process_id) ON DELETE CASCADE,
  lock_ref    TEXT PRIMARY KEY,
  locked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT config_locks_ref_not_blank CHECK (btrim(lock_ref) <> '')
);

-- "Is this process locked" check (TemplateServiceImpl/ProcessServiceImpl edit guards)
CREATE INDEX config_locks_process_idx ON config_locks (process_id);
-- Stale-lock reaper scan (ConfigLockReaper)
CREATE INDEX config_locks_locked_at_idx ON config_locks (locked_at);

-- Carry forward any lock already in flight at deploy time so a mid-upload process doesn't lose
-- its lock across the cutover.
INSERT INTO config_locks (process_id, lock_ref, locked_at)
SELECT process_id, config_lock_ref, coalesce(config_locked_at, now())
FROM processes
WHERE config_locked = TRUE AND config_lock_ref IS NOT NULL;

ALTER TABLE processes
  DROP CONSTRAINT processes_lock_ref_when_locked,
  DROP COLUMN config_locked,
  DROP COLUMN config_lock_ref,
  DROP COLUMN config_locked_at;

COMMENT ON TABLE config_locks IS
  'One row per in-flight upload holding a process''s config lock (S3UploadServiceImpl). A process '
  'is locked against maker-admin edits whenever any row exists for it; releasing one upload''s row '
  'never affects another upload''s row for the same process.';
