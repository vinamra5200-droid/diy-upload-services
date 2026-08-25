-- V1_3_1__drop_upload_attempts_one_active_per_process.sql
-- Purpose: "one active (non-terminal) upload attempt per process" was never an intended product
-- requirement — it blocked a maker from starting a new upload for a process whenever an earlier
-- attempt was still sitting in ACCEPTED/VALIDATING/READY_FOR_DECISION, including across
-- completely unrelated files. Drops the DB-level backstop (V1_0_0) for that rule; the
-- application-level pre-check and its DataIntegrityViolationException handling in
-- UploadAttemptServiceImpl#create are removed in the same change.

DROP INDEX upload_attempts_one_active_per_process_uidx;
