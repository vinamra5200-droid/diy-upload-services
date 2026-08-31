-- V1_4_10__make_queue_configs_topic_name_nullable.sql
-- Purpose: Queue Config create (admin-api-contract.md §7.2) only captures queueConfigName —
-- producer/topic are filled in later via Update (§7.3), same "shell first" pattern the wizard
-- already uses for apiConfigId. topic_name was left NOT NULL from V1_4_0, which made every
-- create() fail bean validation ("topic must not be null") since a freshly created draft has no
-- topic yet. Dropping the NOT NULL lets a draft exist without one; the existing
-- queue_configs_topic_not_blank CHECK and queue_configs_topic_name_ci_uidx unique index both
-- already tolerate NULL (a CHECK passes vacuously on NULL, and Postgres unique indexes don't
-- treat NULLs as duplicates of each other).

ALTER TABLE queue_configs ALTER COLUMN topic_name DROP NOT NULL;
