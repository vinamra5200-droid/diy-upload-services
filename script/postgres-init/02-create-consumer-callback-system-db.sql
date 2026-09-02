-- Runs once, only when the postgres container's data volume is first initialized (official
-- postgres image behavior for /docker-entrypoint-initdb.d) — creates consumer-callback-service's
-- system database on this shared Postgres instance, alongside diy-batch-upload-db (created via
-- POSTGRES_DB) and diy-validation-system-db (01-create-diy-validation-system-db.sql). If the
-- postgres_data volume already existed before this file was added, this won't retroactively run;
-- create the database manually or recreate the volume.
CREATE DATABASE "consumer-callback-system-db";
