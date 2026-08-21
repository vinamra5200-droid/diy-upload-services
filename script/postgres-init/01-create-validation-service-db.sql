-- Runs once, only when the postgres container's data volume is first initialized (official
-- postgres image behavior for /docker-entrypoint-initdb.d) — creates validation-service's
-- database on this shared Postgres instance, alongside mt-template-system-db (created via
-- POSTGRES_DB). If the postgres_data volume already existed before this file was added, this
-- won't retroactively run; create the database manually or recreate the volume.
CREATE DATABASE "validation-service-db";
