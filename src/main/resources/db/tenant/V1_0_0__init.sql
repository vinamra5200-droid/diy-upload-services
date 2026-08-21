    title VARCHAR(100) NOT NULL,
    description TEXT,
    path VARCHAR(255),          -- Route path
    icon VARCHAR(100),          -- Icon key resolved by the frontend
    order_index INTEGER,        -- Sort order within parent
    section_code VARCHAR(60),   -- Sidebar heading this menu is grouped under
    status INTEGER NOT NULL,    -- 1=active, 0=inactive
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_sidebar_menus_menu_code UNIQUE (menu_code),
    CONSTRAINT fk_sidebar_menu_parent FOREIGN KEY (parent_id)
        REFERENCES sidebar_menus(id) ON DELETE CASCADE,
    CONSTRAINT fk_sidebar_menu_created_by FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_sidebar_menu_updated_by FOREIGN KEY (updated_by)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_sidebar_menus_parent_id ON sidebar_menus (parent_id);
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_order ON sidebar_menus (parent_id, order_index);
CREATE INDEX IF NOT EXISTS idx_sidebar_menus_status ON sidebar_menus (status);

-- ---- tenant/V1_0_30__create_example_entity.sql ----
-- V1_0_30__create_example_entity.sql
-- Purpose: Create the example_entity table in EVERY tenant database
--
-- db/tenant migrations run once per tenant database (TenantProvisioningService) as the
-- tenant's own DB role — every tenant DB gets an identical, versioned schema. Single
-- 'public' schema per tenant database (QCP standard); gen_random_uuid() needs no extension.

CREATE TABLE example_entity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0  -- optimistic locking counter managed by JPA @Version
);

-- Index for name lookups (used in WHERE/ORDER BY)
CREATE INDEX idx_example_entity_name ON example_entity(name);
-- Index for time-based listing queries
CREATE INDEX idx_example_entity_created_at ON example_entity(created_at);

-- Audit trigger function: keeps updated_at current on UPDATE
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Audit trigger
CREATE TRIGGER update_example_entity_updated_at
    BEFORE UPDATE ON example_entity
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ---- tenant/V1_0_31__create_diy_upload_helpers.sql ----
-- V1_0_31__create_diy_upload_helpers.sql
-- Purpose: Shared helper functions for the DIY Upload Admin feature tables that follow
-- (V1_0_32 onward) — human-readable ID generation and the updated_at touch trigger.
--
-- Ported from the DIY Batch Upload Framework's standalone schema
-- (D:\Source Code\DIY Batch Upload Framework\diy-upload-web\db\migrations\001_init.sql). The
-- original wrapped everything in a `diy_upload` schema with its own search_path; tenant
-- databases use a single `public` schema (AGENTS.md rule 18), so that wrapper is dropped here
-- and every `diy_upload.` prefix is dropped from the tables that follow.

CREATE OR REPLACE FUNCTION generate_id(prefix TEXT)
RETURNS TEXT
LANGUAGE sql
VOLATILE
AS $$
  SELECT prefix || '-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8);
$$;

-- Note: application code (utils/IdGenerator.java) generates IDs in this same format before every
-- INSERT — Hibernate always includes the mapped @Id column, so this DEFAULT only fires for
-- inserts made outside the JPA layer (defensive fallback, matching the source schema).

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---- tenant/V1_0_32__create_processes.sql ----
-- V1_0_32__create_processes.sql
-- Purpose: Upload process definitions. Maker drafts; Checker activates
-- (admin-api-contract.md §1). Postgres native enums from the source schema become
-- VARCHAR + CHECK here (see V1_0_31 header) so they map to @Enumerated(EnumType.STRING) with no
-- custom Hibernate type.

CREATE TABLE processes (
  process_id                TEXT PRIMARY KEY DEFAULT generate_id('proc'),
  process_name              TEXT NOT NULL,
  description               TEXT NOT NULL DEFAULT '',
  status                    VARCHAR(20) NOT NULL DEFAULT 'draft'
                             CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  validations_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  validations_skip_reason   TEXT,
  config_locked             BOOLEAN NOT NULL DEFAULT FALSE,
  config_lock_ref           TEXT,
  submitted_by              TEXT,
  rejection_reason          TEXT,
  created_by                TEXT NOT NULL,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT processes_name_not_blank CHECK (btrim(process_name) <> ''),
  CONSTRAINT processes_name_len CHECK (char_length(process_name) <= 120),
  CONSTRAINT processes_description_len CHECK (char_length(description) <= 500),
  CONSTRAINT processes_skip_reason_when_disabled
    CHECK (validations_enabled OR btrim(coalesce(validations_skip_reason, '')) <> ''),
  CONSTRAINT processes_lock_ref_when_locked
    CHECK (NOT config_locked OR btrim(coalesce(config_lock_ref, '')) <> ''),
  CONSTRAINT processes_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

-- Case-insensitive uniqueness on process name (create/update conflict checks)
CREATE UNIQUE INDEX processes_name_ci_uidx ON processes (lower(process_name));
-- Status filter (list endpoint's ?status= query param)
CREATE INDEX processes_status_idx ON processes (status);
-- Checker inbox lookup
CREATE INDEX processes_inbox_idx ON processes (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER processes_set_updated_at
  BEFORE UPDATE ON processes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE processes IS 'Upload process definitions. Maker drafts; Checker activates.';

-- ---- tenant/V1_0_33__create_database_connections.sql ----
-- V1_0_33__create_database_connections.sql
-- Purpose: Database connections — standalone admin resource and the target for a template's
-- Post-Load Action (postLoadAction.databaseMode = "useExisting") (admin-api-contract.md §6).

CREATE TABLE database_connections (
  connection_id     TEXT PRIMARY KEY DEFAULT generate_id('db'),
  provider          VARCHAR(20) NOT NULL CHECK (provider IN ('POSTGRES','MYSQL','SQL_SERVER','ORACLE')),
  connection_label  TEXT NOT NULL,
  connection_ref    TEXT NOT NULL DEFAULT '<set-in-ci>',
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  updated_by        TEXT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT database_connections_label_not_blank CHECK (btrim(connection_label) <> ''),
  CONSTRAINT database_connections_label_len CHECK (char_length(connection_label) <= 120),
  CONSTRAINT database_connections_ref_len CHECK (char_length(connection_ref) <= 500),
  CONSTRAINT database_connections_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX database_connections_label_ci_uidx ON database_connections (lower(connection_label));
CREATE INDEX database_connections_status_idx ON database_connections (status);
CREATE INDEX database_connections_inbox_idx ON database_connections (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER database_connections_set_updated_at
  BEFORE UPDATE ON database_connections
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN database_connections.connection_ref IS
  'Shape-only reference (connection string name / secret alias). Never store live credentials here — resolve via secrets manager at runtime.';

-- ---- tenant/V1_0_34__create_database_connection_tables.sql ----
-- V1_0_34__create_database_connection_tables.sql
-- Purpose: Target table names exposed by a database connection, for a template's Post-Load
-- Action table picker (admin-api-contract.md §6.2 tableNames).

CREATE TABLE database_connection_tables (
  connection_id   TEXT NOT NULL REFERENCES database_connections (connection_id) ON DELETE CASCADE,
  table_name      TEXT NOT NULL,
  sort_order      INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (connection_id, table_name)
);

CREATE INDEX database_connection_tables_order_idx ON database_connection_tables (connection_id, sort_order);

COMMENT ON TABLE database_connection_tables IS
  'Target table names exposed by a database connection for template post-load-action selection.';

-- ---- tenant/V1_0_35__create_storage_configs.sql ----
-- V1_0_35__create_storage_configs.sql
-- Purpose: Interim object-store connections for pending-submission holding (admin-api-contract.md
-- §5). List+create+edit like storage/database/api-config — not a singleton.

CREATE TABLE storage_configs (
  config_id         TEXT PRIMARY KEY DEFAULT generate_id('stg'),
  provider          VARCHAR(20) NOT NULL CHECK (provider IN ('AWS_S3','AZURE_BLOB','GCS','ON_PREM')),
  connection_label  TEXT NOT NULL,
  connection_ref    TEXT NOT NULL DEFAULT '<set-in-ci>',
  path_pattern      TEXT NOT NULL DEFAULT 'diy-upload/{env}/{process_id}/…',
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  updated_by        TEXT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT storage_configs_label_not_blank CHECK (btrim(connection_label) <> ''),
  CONSTRAINT storage_configs_label_len CHECK (char_length(connection_label) <= 120),
  CONSTRAINT storage_configs_ref_len CHECK (char_length(connection_ref) <= 500),
  CONSTRAINT storage_configs_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX storage_configs_label_ci_uidx ON storage_configs (lower(connection_label));
CREATE INDEX storage_configs_status_idx ON storage_configs (status);
CREATE INDEX storage_configs_inbox_idx ON storage_configs (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER storage_configs_set_updated_at
  BEFORE UPDATE ON storage_configs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN storage_configs.connection_ref IS 'Shape-only reference (ARN / connection name). Never store live secrets here.';
COMMENT ON TABLE storage_configs IS 'Interim object-store connections. List+create+edit like storage/database/api-config — not a singleton.';

-- ---- tenant/V1_0_36__create_api_configs.sql ----
-- V1_0_36__create_api_configs.sql
-- Purpose: Reusable outbound HTTP call definitions (admin-api-contract.md §7).

CREATE TABLE api_configs (
  config_id         TEXT PRIMARY KEY DEFAULT generate_id('apiconfig'),
  label             TEXT NOT NULL,
  method            VARCHAR(10) NOT NULL DEFAULT 'GET' CHECK (method IN ('GET','POST','PUT','PATCH','DELETE')),
  uri               TEXT NOT NULL,
  query_params      JSONB NOT NULL DEFAULT '[]',
  headers           JSONB NOT NULL DEFAULT '[]',
  body              TEXT NOT NULL DEFAULT '',
  auth              JSONB NOT NULL DEFAULT '{"type":"none","username":"","password":"","token":"","apiKeyName":"","apiKeyValue":"","apiKeyLocation":"header"}',
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  updated_by        TEXT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT api_configs_label_not_blank CHECK (btrim(label) <> ''),
  CONSTRAINT api_configs_uri_not_blank CHECK (btrim(uri) <> ''),