-- Runs once, only when the postgres container's data volume is first initialized (official
-- postgres image behavior for /docker-entrypoint-initdb.d) — creates diy-validation-service's
-- system database on this shared Postgres instance, alongside diy-batch-upload-db (created via
-- POSTGRES_DB). If the postgres_data volume already existed before this file was added, this
-- won't retroactively run; create the database manually or recreate the volume.
CREATE DATABASE "diy-validation-system-db";
