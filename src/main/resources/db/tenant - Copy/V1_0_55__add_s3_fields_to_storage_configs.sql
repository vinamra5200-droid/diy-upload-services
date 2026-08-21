-- V1_0_55__add_s3_fields_to_storage_configs.sql
-- Purpose: capture AWS S3 connection details as first-class fields instead of the generic
-- connection_ref placeholder, so the Storage admin UI can prompt for them directly
-- (admin-api-contract.md §5). Applies to provider = 'AWS_S3'; other providers are unaffected
-- and keep using connection_ref.

-- IF NOT EXISTS on every clause: this migration was never recorded as applied by Flyway on any
-- tenant (all three "qc"/"client1"/"client2" showed 1.0.55 as unapplied when this repo's
-- migration set was first validated against them), but "qc" already had these exact columns —
-- added out-of-band, outside Flyway, at some earlier point. Idempotent so it's a correct no-op
-- there and a real ALTER on client1/client2 (or any future tenant) alike.
ALTER TABLE storage_configs
  ADD COLUMN IF NOT EXISTS bucket_name       TEXT,
  ADD COLUMN IF NOT EXISTS bucket_region     TEXT,
  ADD COLUMN IF NOT EXISTS access_key_id     TEXT,
  ADD COLUMN IF NOT EXISTS secret_access_key TEXT,
  ADD COLUMN IF NOT EXISTS hostname          TEXT,
  ADD COLUMN IF NOT EXISTS port              INTEGER;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'storage_configs_port_range'
  ) THEN
    ALTER TABLE storage_configs
      ADD CONSTRAINT storage_configs_port_range
        CHECK (port IS NULL OR port BETWEEN 1 AND 65535);
  END IF;
END $$;

COMMENT ON COLUMN storage_configs.bucket_name IS
  'AWS_S3 only: target bucket name.';
COMMENT ON COLUMN storage_configs.bucket_region IS
  'AWS_S3 only: bucket region, e.g. ap-south-1.';
COMMENT ON COLUMN storage_configs.access_key_id IS
  'AWS_S3 only: AWS access key id. Not itself secret, but rotate alongside secret_access_key.';
COMMENT ON COLUMN storage_configs.secret_access_key IS
  'AWS_S3 only. Sensitive — never returned in full by the API (masked in StorageConfigResponse). '
  'Stored as plain text for now; encrypt at rest (e.g. pgcrypto pgp_sym_encrypt, or an '
  'application-level KMS envelope) before handling real production credentials.';
COMMENT ON COLUMN storage_configs.hostname IS
  'AWS_S3 only: S3 endpoint hostname, e.g. s3.amazonaws.com (or a region / VPC-endpoint / '
  'S3-compatible host).';
COMMENT ON COLUMN storage_configs.port IS
  'AWS_S3 only: endpoint port, typically 443.';
