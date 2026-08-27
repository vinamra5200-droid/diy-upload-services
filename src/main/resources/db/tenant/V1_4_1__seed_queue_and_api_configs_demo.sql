-- V1_4_1__seed_queue_and_api_configs_demo.sql
-- Purpose: reference/demo rows for api_configs and Queue Orchestration (queue_configs) — same
-- "every tenant gets it" reasoning as V1_1_0's vendor-onboarding seed, so the Admin > API
-- Configuration and Admin > Queue Orchestration screens aren't empty on a fresh tenant database.
-- One `active` + one `waitingForChecker` row each, so both the maker and checker demo flows have
-- something to exercise; the waitingForChecker queue config is deliberately left with
-- api_config_id = NULL so the "Consumer Callback" step's empty state has something to demo too.
--
-- submitted_by/updated_by/created_by reuse the dev-bypass actor ids seeded by V1_1_1
-- (maker_admin_01 / checker_admin_01).
--
-- ON CONFLICT DO NOTHING makes this safe to re-run manually (e.g. via psql) even though Flyway
-- itself only ever applies a versioned migration once per tenant database.

-- ---- Outbound API Configs ----

INSERT INTO api_configs (
  config_id, label, method, uri, query_params, headers, body, auth,
  status, submitted_by, rejection_reason, updated_by, updated_at
) VALUES
(
  'apiconfig-demo-kyc',
  'KYC verification webhook',
  'POST',
  'https://api.example.com/v1/kyc/verify',
  '[{"key":"source","value":"diy-upload"}]'::jsonb,
  '[{"key":"Content-Type","value":"application/json"}]'::jsonb,
  '{"customerId":"","pan":""}',
  '{"type":"bearer","username":"","password":"","token":"<set-in-ci>","apiKeyName":"","apiKeyValue":"","apiKeyLocation":"header"}'::jsonb,
  'active', 'maker_admin_01', NULL, 'maker_admin_01', '2026-08-01T11:00:00.000Z'
),
(
  'apiconfig-demo-loan-disb',
  'Loan disbursement notifier',
  'POST',
  'https://api.example.com/v1/loans/disbursement-notify',
  '[]'::jsonb,
  '[{"key":"Content-Type","value":"application/json"}]'::jsonb,
  '{"loanId":"","status":"disbursed"}',
  '{"type":"apiKey","username":"","password":"","token":"","apiKeyName":"X-Api-Key","apiKeyValue":"<set-in-ci>","apiKeyLocation":"header"}'::jsonb,
  'waitingForChecker', 'maker_admin_01', NULL, 'maker_admin_01', '2026-08-12T09:30:00.000Z'
)
ON CONFLICT (config_id) DO NOTHING;

-- ---- Queue Orchestration (Kafka producer + topic + consumer callback contract) ----

INSERT INTO queue_configs (
  queue_config_id, queue_config_name, description,
  producer_client_id, producer_acks, producer_batch_size_kb, producer_linger_ms,
  producer_compression_type, producer_retries, producer_max_in_flight_requests,
  topic_name, topic_bootstrap_servers, topic_partitions, topic_replication_factor,
  topic_retention_hours, topic_cleanup_policy,
  api_config_id, status, submitted_by, rejection_reason, created_by, created_at, updated_at
) VALUES
(
  'queue-demo-kyc-events',
  'KYC verification events',
  'Publishes validated KYC batch rows for downstream verification',
  'diy-upload-producer', 'all', 16, 5, 'snappy', 3, 5,
  'kyc-verification-events', 'kafka.internal:9092', 3, 1, 168, 'delete',
  'apiconfig-demo-kyc', 'active', 'maker_admin_01', NULL, 'maker_admin_01',
  '2026-08-01T11:05:00.000Z', '2026-08-01T11:05:00.000Z'
),
(
  'queue-demo-loan-disb-events',
  'Loan disbursement events',
  'Publishes disbursement completion events once a loan batch is loaded — awaiting Checker Admin approval, and awaiting its consumer callback binding (Step 3)',
  'diy-upload-producer', '1', 32, 0, 'none', 3, 5,
  'loan-disbursement-events', 'kafka.internal:9092', 6, 1, 168, 'delete',
  NULL, 'waitingForChecker', 'maker_admin_01', NULL, 'maker_admin_01',
  '2026-08-12T09:35:00.000Z', '2026-08-12T09:35:00.000Z'
)
ON CONFLICT (queue_config_id) DO NOTHING;
