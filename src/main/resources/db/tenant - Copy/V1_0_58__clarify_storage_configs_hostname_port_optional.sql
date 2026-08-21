-- V1_0_58__clarify_storage_configs_hostname_port_optional.sql
-- Purpose: hostname/port were being treated (by the admin UI's validation and default values) as
-- required for provider = AWS_S3, on the mistaken assumption every AWS_S3 connection needs an
-- explicit endpoint. In fact the AWS SDK resolves the correct regional endpoint from bucket_region
-- alone when hostname/port are left null; the fields exist only to override that for an
-- S3-compatible/on-prem store (e.g. MinIO) or a VPC endpoint. The UI defaulting new connections to
-- hostname = 's3.amazonaws.com' actively broke uploads for any bucket outside us-east-1 (AWS
-- rejects path-style requests to the global endpoint for a non-us-east-1 bucket with
-- PermanentRedirect). No column/constraint changes needed — both were already nullable — this
-- just corrects the comments to match the fix.

COMMENT ON COLUMN storage_configs.hostname IS
  'AWS_S3 only, and optional: set only for an S3-compatible/on-prem store (e.g. MinIO) or a VPC '
  'endpoint. Leave NULL for standard AWS S3 — the AWS SDK resolves the correct regional endpoint '
  'from bucket_region on its own; a non-NULL hostname forces path-style addressing to that literal '
  'host instead.';
COMMENT ON COLUMN storage_configs.port IS
  'AWS_S3 only, and optional: only meaningful alongside a non-NULL hostname (defaults to 443 if '
  'hostname is set but this is left NULL). Leave both NULL for standard AWS S3.';
