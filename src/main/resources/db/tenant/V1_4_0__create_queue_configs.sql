-- V1_4_0__create_queue_configs.sql
-- Purpose: Queue Orchestration — reusable Kafka producer + topic settings bound to a consumer
-- callback contract (an existing api_configs row), so a template's Kafka post-load action can bind
-- to a saved queue by topic name instead of typing kafka_topic/kafka_bootstrap_servers by hand
-- (admin-api-contract.md §7). Adds templates.kafka_mode/kafka_queue_config_id — mirrors the
-- existing database_mode/database_connection_id pair from V1_0_37 — and extends v_checker_inbox
-- (V1_0_51) with a queueConfig branch.
--
-- Guarded with IF NOT EXISTS / OR REPLACE throughout so this migration is safe to re-apply
-- manually (e.g. via psql) even though Flyway itself only ever runs a versioned migration once
-- per tenant database.

CREATE TABLE IF NOT EXISTS queue_configs (
  queue_config_id                 TEXT PRIMARY KEY DEFAULT generate_id('queue'),
  queue_config_name               TEXT NOT NULL,
  description                     TEXT NOT NULL DEFAULT '',

  producer_client_id              TEXT NOT NULL DEFAULT '',
  producer_acks                   VARCHAR(3) NOT NULL DEFAULT '1'
                                   CHECK (producer_acks IN ('0','1','all')),
  producer_batch_size_kb          INTEGER NOT NULL DEFAULT 16
                                   CHECK (producer_batch_size_kb BETWEEN 1 AND 1000),
  producer_linger_ms              INTEGER NOT NULL DEFAULT 0 CHECK (producer_linger_ms >= 0),
  producer_compression_type       VARCHAR(10) NOT NULL DEFAULT 'none'
                                   CHECK (producer_compression_type IN ('none','gzip','snappy','lz4','zstd')),
  producer_retries                INTEGER NOT NULL DEFAULT 3 CHECK (producer_retries >= 0),
  producer_max_in_flight_requests INTEGER NOT NULL DEFAULT 5
                                   CHECK (producer_max_in_flight_requests >= 1),

  topic_name                      TEXT NOT NULL,
  topic_bootstrap_servers         TEXT NOT NULL DEFAULT '',
  topic_partitions                INTEGER NOT NULL DEFAULT 3 CHECK (topic_partitions >= 1),
  topic_replication_factor        INTEGER NOT NULL DEFAULT 1 CHECK (topic_replication_factor >= 1),
  topic_retention_hours           INTEGER NOT NULL DEFAULT 168 CHECK (topic_retention_hours >= 1),
  topic_cleanup_policy            VARCHAR(10) NOT NULL DEFAULT 'delete'
                                   CHECK (topic_cleanup_policy IN ('delete','compact')),

  api_config_id                   TEXT REFERENCES api_configs (config_id) ON DELETE RESTRICT,

  status                          VARCHAR(20) NOT NULL DEFAULT 'draft'
                                   CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by                    TEXT,
  rejection_reason                TEXT,
  created_by                      TEXT NOT NULL,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT queue_configs_name_not_blank CHECK (btrim(queue_config_name) <> ''),
  CONSTRAINT queue_configs_topic_not_blank CHECK (btrim(topic_name) <> ''),
  CONSTRAINT queue_configs_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX IF NOT EXISTS queue_configs_topic_name_ci_uidx ON queue_configs (lower(topic_name));
CREATE INDEX IF NOT EXISTS queue_configs_status_idx ON queue_configs (status);
CREATE INDEX IF NOT EXISTS queue_configs_inbox_idx ON queue_configs (status) WHERE status = 'waitingForChecker';
CREATE INDEX IF NOT EXISTS queue_configs_api_config_idx ON queue_configs (api_config_id);

DROP TRIGGER IF EXISTS queue_configs_set_updated_at ON queue_configs;
CREATE TRIGGER queue_configs_set_updated_at
  BEFORE UPDATE ON queue_configs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN queue_configs.api_config_id IS
  'References the Outbound API Config the consumer calls once it dequeues a message from this topic ("Consumer Callback" wizard step). Nullable until that step is completed.';

-- ---- Bind a template's Kafka post-load action to a saved queue config ----

ALTER TABLE templates
  ADD COLUMN IF NOT EXISTS kafka_mode VARCHAR(20)
    CHECK (kafka_mode IS NULL OR kafka_mode IN ('useExisting','custom')),
  ADD COLUMN IF NOT EXISTS kafka_queue_config_id TEXT
    REFERENCES queue_configs (queue_config_id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS templates_queue_config_idx ON templates (kafka_queue_config_id);

COMMENT ON COLUMN templates.kafka_mode IS
  'When post_load_action_type = kafka: useExisting binds kafka_queue_config_id (a saved Queue Orchestration config, which supplies the topic/producer/consumer-callback settings); custom (or NULL, for templates saved before Queue Orchestration existed) uses kafka_topic/kafka_bootstrap_servers directly.';

-- ---- Extend the checker inbox with queue configs ----

CREATE OR REPLACE VIEW v_checker_inbox AS
SELECT
  'chg-process-' || p.process_id                          AS change_id,
  'process'                                                AS entity_type,
  p.process_id                                            AS entity_id,
  p.process_name                                          AS entity_label,
  'Process ' || p.process_id || ' awaiting Checker Admin' AS summary,
  coalesce(p.submitted_by, p.created_by)                  AS submitted_by,
  p.updated_at                                            AS submitted_at,
  TRUE                                                    AS actor_ne_submitter,
  NULL::TEXT                                              AS process_id_ref
FROM processes p
WHERE p.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-template-' || t.template_id,
  'template',
  t.template_id,
  t.template_code || ' v' || t.version,
  'Template ' || t.template_code || ' awaiting Checker Admin',
  coalesce(t.submitted_by, t.created_by),
  t.updated_at,
  TRUE,
  t.process_id
FROM templates t
WHERE t.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-role-' || r.role_id,
  'role',
  r.role_id,
  r.role_name,
  'Upload role ' || r.role_name || ' awaiting Checker Admin',
  coalesce(r.submitted_by, r.created_by),
  r.updated_at,
  TRUE,
  NULL
FROM upload_roles r
WHERE r.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-user-' || u.user_id,
  'user',
  u.user_id,
  u.full_name,
  'Maker user ' || u.username || ' awaiting Checker Admin',
  coalesce(u.submitted_by, u.created_by),
  u.updated_at,
  TRUE,
  NULL
FROM maker_users u
WHERE u.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-storage-' || s.config_id,
  'storage',
  s.config_id,
  s.connection_label,
  'Storage connection ' || s.connection_label || ' awaiting Checker Admin',
  coalesce(s.submitted_by, s.updated_by),
  s.updated_at,
  TRUE,
  NULL
FROM storage_configs s
WHERE s.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-database-' || d.connection_id,
  'database',
  d.connection_id,
  d.connection_label,
  'Database connection ' || d.connection_label || ' awaiting Checker Admin',
  coalesce(d.submitted_by, d.updated_by),
  d.updated_at,
  TRUE,
  NULL
FROM database_connections d
WHERE d.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-apiconfig-' || a.config_id,
  'apiConfig',
  a.config_id,
  a.label,
  'API configuration ' || a.label || ' awaiting Checker Admin',
  coalesce(a.submitted_by, a.updated_by),
  a.updated_at,
  TRUE,
  NULL
FROM api_configs a
WHERE a.status = 'waitingForChecker'

UNION ALL

SELECT
  'chg-queueconfig-' || q.queue_config_id,
  'queueConfig',
  q.queue_config_id,
  q.queue_config_name,
  'Queue configuration ' || q.queue_config_name || ' awaiting Checker Admin',
  coalesce(q.submitted_by, q.created_by),
  q.updated_at,
  TRUE,
  NULL
FROM queue_configs q
WHERE q.status = 'waitingForChecker';

COMMENT ON VIEW v_checker_inbox IS
  'Pending Maker submissions across all governed entities. API must hide rows where submitted_by = current actor (four-eyes). process_id_ref is populated for templates only, to build a review link back to their parent process.';
