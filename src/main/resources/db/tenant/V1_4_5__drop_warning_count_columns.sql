-- V1_4_5__drop_warning_count_columns.sql
-- Purpose: The WARNING/ERROR rule severity distinction is deprecated on the validation-service
-- side (every rule violation now fails the row — see diy-validation-service's own
-- V1_2_0__drop_batch_upload_warning_count.sql), so there is no longer a separate
-- passed-with-warning count anywhere downstream either. Drops upload_jobs.warning_records
-- (V1_0_0) and batch_upload_results.warning_count (V1_0_64, folded into V1_0_0__init.sql).

ALTER TABLE upload_jobs DROP COLUMN warning_records;

ALTER TABLE batch_upload_results DROP COLUMN warning_count;
