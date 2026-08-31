-- V1_4_8__drop_validation_worker_threads.sql
-- Purpose: templates.validation_worker_threads (V1_0_0) was never read by any worker-pool sizing
-- logic — it was plumbed through the entity/DTOs and back but nothing consumed it. Drops the
-- column; the corresponding application-level fields/entity/DTOs are removed in the same change.

ALTER TABLE templates DROP COLUMN validation_worker_threads;
