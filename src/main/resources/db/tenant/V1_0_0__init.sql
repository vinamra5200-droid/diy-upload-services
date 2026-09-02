-- V1_0_0__init.sql
-- Purpose: Full tenant-database schema, consolidated into one migration file (see
-- the individual migrations previously under db/tenant/, preserved for reference in
-- 'db/tenant - Copy'). Run once per tenant database by TenantProvisioningService, as the
-- tenant's own DB role (AGENTS.md rule 18) — never by Spring Boot's own Flyway at startup
-- (that one only runs db/migration, the system DB).
--
-- This file was re-consolidated to fold in every migration that had accumulated on top of the
-- previous V1_0_0 baseline (V1_1_0 through V1_4_20 — seeds, the upload-operator pipeline columns,
-- Queue Orchestration, and the column/table drops that followed). Those ~30 files are preserved
-- for reference in 'db/tenant - Copy'; this is once again the only file Flyway applies to a new
-- tenant database. Sections are concatenated in their original apply order, so later sections'
-- ALTER/DROP statements act on tables/columns created earlier in this same file — exactly as they
-- did when they ran as separate versioned migrations.
--
-- Two DROP COLUMN statements from the original migrations are intentionally NOT carried forward:
-- V1_4_4's drops of templates.upload_process_timeout_minutes/upload_attempts.timeout_minutes and
-- V1_4_8's drop of templates.validation_worker_threads. All three columns were already absent from
-- the prior V1_0_0 baseline (added by pre-squash migrations that were never folded in, and dropped
-- again downstream) — every currently deployed tenant DB has them thanks to its real un-squashed
-- history, but this baseline never did, so re-running those DROPs here would fail with "column
-- does not exist" against a genuinely fresh tenant. Omitting them yields the same final state
-- (column absent) without the error. No current entity/DTO references any of the three (verified
-- against src/main/java), so this affects only what a brand-new tenant's schema looks like along
-- the way, never runtime behavior.
--
-- ${tenant_code} is a live Flyway placeholder, not a literal to resolve here —
-- TenantProvisioningService#migrate supplies it per tenant (Map.of("tenant_code", tenantCode))
-- via Flyway.configure().placeholders(...), so this same file seeds correct rows for qc,
-- client1, client2, and any future tenant without modification.
--
-- No explicit BEGIN/COMMIT: Flyway (mixed=false, the default — see
-- TenantProvisioningService#migrate) already wraps this whole script in one transaction.
-- Adding manual transaction control here would fight Flyway's own commit and can leave
-- flyway_schema_history out of sync with what actually landed.
--
-- IMPORTANT for existing tenant databases (qc/client1/client2, or any other already-provisioned
-- tenant): this file replaces flyway_schema_history entries V1_0_0 through V1_4_20 with a single
-- V1_0_0 entry. Every existing tenant DB already has this exact schema from having run those
-- migrations individually, but Flyway will now see a checksum mismatch on V1_0_0 and "applied
-- migration not resolved locally" for every version this file absorbed. Each existing tenant
-- database needs its flyway_schema_history repaired (e.g. `flyway repair` after deleting the old
-- V1_1_0..V1_4_20 rows and updating V1_0_0's checksum, or an equivalent manual rewrite) before its
-- next restart/provisioning touch — this migration set intentionally does not attempt that itself.

-- ---- V1_0_0__create_images.sql ----
-- V1_0_0__create_images.sql
-- Purpose: Binary asset store per tenant DB (profile pictures, documents, etc.).

-- =============================================
-- Tenant images table
-- =============================================

CREATE TABLE IF NOT EXISTS images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name TEXT NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    file_data BYTEA NOT NULL,
    description TEXT NOT NULL,
    uploaded_by_type INTEGER NOT NULL,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reference_id UUID
);

CREATE INDEX IF NOT EXISTS idx_images_uploaded_by ON images(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_images_file_name ON images(file_name);
CREATE INDEX IF NOT EXISTS idx_images_reference_id ON images(reference_id);

-- ---- V1_0_1__create_users.sql ----
-- V1_0_1__create_users.sql
-- Purpose: User registry scoped to each tenant database.

-- =============================================
-- Tenant users table
-- =============================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email_id VARCHAR(255) NOT NULL UNIQUE,
    mobile_number VARCHAR(10) NOT NULL UNIQUE,
    send_activation_email INTEGER NOT NULL,
    send_activation_sms INTEGER NOT NULL,
    password VARCHAR(500),
    password_creation_date TIMESTAMP WITH TIME ZONE,
    password_expiry_days INTEGER,
    password_invalid_attempts INTEGER,
    password_activation_key UUID,
    password_activation_key_valid_to TIMESTAMP WITH TIME ZONE,
    password_reset_key UUID,
    password_reset_key_valid_to TIMESTAMP WITH TIME ZONE,
    profile_image_id UUID,
    reporting_manager_id UUID,
    status INTEGER NOT NULL,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_reporting_manager_id FOREIGN KEY (reporting_manager_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email_id ON users(email_id);
CREATE INDEX IF NOT EXISTS idx_users_profile_image_id ON users(profile_image_id);

-- Add foreign key for user profile image
ALTER TABLE users
    ADD CONSTRAINT fk_users_profile_image
        FOREIGN KEY (profile_image_id)
            REFERENCES images(id)
            ON DELETE SET NULL;

-- ---- V1_0_2__create_roles.sql ----
-- V1_0_2__create_roles.sql
-- Purpose: Role catalog scoped to each tenant database.

-- =============================================
-- Tenant roles table
-- =============================================

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_type INTEGER NOT NULL,
    name VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_roles_role_type ON roles(role_type);
CREATE INDEX IF NOT EXISTS idx_roles_status ON roles(status);

-- ---- V1_0_3__create_api_clients.sql ----
-- V1_0_3__create_api_clients.sql
-- Purpose: Machine-to-machine client registry scoped to each tenant database.

-- =============================================
-- Tenant API clients table
-- =============================================

CREATE TABLE IF NOT EXISTS api_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id VARCHAR(50) NOT NULL UNIQUE,
    client_secret VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    email_id VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    status INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_api_clients_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_api_clients_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_api_clients_client_id ON api_clients(client_id);
CREATE INDEX IF NOT EXISTS idx_api_clients_status ON api_clients(status);

-- ---- V1_0_4__create_api_client_user_roles.sql ----
-- V1_0_4__create_api_client_user_roles.sql
-- Purpose: Role assignments for tenant users and API clients.

-- =============================================
-- Tenant API client user roles table
-- =============================================

CREATE TABLE IF NOT EXISTS api_client_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    api_client_id UUID,
    role_id UUID NOT NULL,
    status INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_acur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_api_client FOREIGN KEY (api_client_id) REFERENCES api_clients(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_acur_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_acur_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_acur_user_role ON api_client_user_roles(user_id, api_client_id, role_id);

-- ---- V1_0_5__create_api_client_user_tokens.sql ----
-- V1_0_5__create_api_client_user_tokens.sql
-- Purpose: JWT access/refresh token lifecycle management for tenant principals.

-- =============================================
-- Tenant API client user tokens table
-- =============================================

CREATE TABLE IF NOT EXISTS api_client_user_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_type INTEGER NOT NULL,
    user_id UUID,
    api_client_id UUID,
    token_type INTEGER NOT NULL,
    token VARCHAR(1024) NOT NULL,
    user_agent VARCHAR(512),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status INTEGER NOT NULL,
    created_by_type INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_type INTEGER,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_acut_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_acut_client_id FOREIGN KEY (api_client_id) REFERENCES api_clients(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_acut_user_token ON api_client_user_tokens(user_id, token);
CREATE INDEX IF NOT EXISTS idx_acut_api_client_id ON api_client_user_tokens(api_client_id);
CREATE INDEX IF NOT EXISTS idx_acut_status ON api_client_user_tokens(status);

-- ---- V1_0_6__create_api_client_user_activity_logs.sql ----
-- V1_0_6__create_api_client_user_activity_logs.sql
-- Purpose: Audit trail for tenant-scoped login/logout and other events.

-- =============================================
-- Tenant API client user activity logs table
-- =============================================

CREATE TABLE IF NOT EXISTS api_client_user_activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    api_client_id UUID,
    event_type INTEGER NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    latitude DECIMAL(9,6),
    longitude DECIMAL(9,6),
    app_version VARCHAR(50),
    device_id VARCHAR(100),
    created_by_type INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_acual_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_acual_client_id FOREIGN KEY (api_client_id) REFERENCES api_clients(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_acual_user_id ON api_client_user_activity_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_acual_api_client_id ON api_client_user_activity_logs(api_client_id);
CREATE INDEX IF NOT EXISTS idx_acual_event_type ON api_client_user_activity_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_acual_created_at ON api_client_user_activity_logs(created_at);

-- ---- V1_0_7__create_sidebar_menus.sql ----
-- V1_0_7__create_sidebar_menus.sql  (TENANT database)
-- Purpose: A tenant's own sidebar navigation.
--
-- Why this exists when auth.sidebar_menus already does: they are different sets of rows, not one
-- set filtered two ways. The system database's menus are the administrator's console, and those
-- screens are backed by tables a tenant database does not have — serving them on a tenant host
-- would produce a sidebar whose every entry fails. A tenant reads this table and nothing else.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sidebar_menus (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_type INTEGER,          -- 1=Menu, 2=Sub Menu, 3=Sub Menu Item, 4..6=deeper nesting
    parent_id UUID,             -- Self-reference for hierarchy (nullable for root menus)
    menu_code VARCHAR(80),      -- Stable key the frontend addresses this menu by — never the title
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

-- ---- V1_0_30__create_example_entity.sql ----
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

-- ---- V1_0_31__create_diy_upload_helpers.sql ----
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

-- ---- V1_0_32__create_processes.sql ----
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

-- ---- V1_0_33__create_database_connections.sql ----
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

-- ---- V1_0_34__create_database_connection_tables.sql ----
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

-- ---- V1_0_35__create_storage_configs.sql ----
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

-- ---- V1_0_36__create_api_configs.sql ----
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
  CONSTRAINT api_configs_query_params_is_array CHECK (jsonb_typeof(query_params) = 'array'),
  CONSTRAINT api_configs_headers_is_array CHECK (jsonb_typeof(headers) = 'array'),
  CONSTRAINT api_configs_auth_is_object CHECK (jsonb_typeof(auth) = 'object'),
  CONSTRAINT api_configs_rejection_when_rejected
    CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX api_configs_label_ci_uidx ON api_configs (lower(label));
CREATE INDEX api_configs_status_idx ON api_configs (status);
CREATE INDEX api_configs_inbox_idx ON api_configs (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER api_configs_set_updated_at
  BEFORE UPDATE ON api_configs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN api_configs.auth IS
  'Shape-only credential fields. Never store live secrets here — resolve via secrets manager at runtime. Shape: {type, username, password, token, apiKeyName, apiKeyValue, apiKeyLocation}.';
COMMENT ON COLUMN api_configs.query_params IS 'Array of {key, value} pairs.';
COMMENT ON COLUMN api_configs.headers IS 'Array of {key, value} pairs.';

-- ---- V1_0_37__create_templates.sql ----
-- V1_0_37__create_templates.sql
-- Purpose: Upload templates — column mapping, formats, data-load, post-load action, maker-checker
-- and schedule settings (admin-api-contract.md §2). The source schema's
-- `data_load_primary_key_note` column is a documented-unused placeholder (its comment says so
-- explicitly) and is dropped here; the primary-key field list lives in template_pk_fields
-- (V1_0_40), same as the source.

CREATE TABLE templates (
  template_id                     TEXT PRIMARY KEY DEFAULT generate_id('tmpl'),
  template_code                   TEXT NOT NULL,
  template_name                   TEXT NOT NULL,
  template_description            TEXT NOT NULL DEFAULT '',
  version                         TEXT NOT NULL DEFAULT '1.0.0',
  process_id                      TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  status                          VARCHAR(20) NOT NULL DEFAULT 'draft'
                                   CHECK (status IN ('draft','waitingForChecker','active','rejected')),

  package_max_size_mb             INTEGER NOT NULL DEFAULT 50,
  package_max_rows                INTEGER,

  duplicate_action                VARCHAR(20) NOT NULL DEFAULT 'reject'
                                   CHECK (duplicate_action IN ('reject','skipSilent','overwrite')),
  row_order                       VARCHAR(20) NOT NULL DEFAULT 'inputSequence'
                                   CHECK (row_order IN ('inputSequence','sortByKey')),

  post_load_action_type           VARCHAR(20) NOT NULL DEFAULT 'kafka'
                                   CHECK (post_load_action_type IN ('kafka','database')),
  kafka_topic                     TEXT,
  kafka_bootstrap_servers         TEXT,
  database_mode                   VARCHAR(20) CHECK (database_mode IS NULL OR database_mode IN ('useExisting','custom')),
  database_connection_id          TEXT REFERENCES database_connections (connection_id) ON DELETE RESTRICT,
  database_provider               VARCHAR(20) CHECK (database_provider IS NULL OR database_provider IN ('POSTGRES','MYSQL','SQL_SERVER','ORACLE')),
  database_connection_ref         TEXT,
  database_table_name             TEXT,

  validations_enabled             BOOLEAN NOT NULL DEFAULT TRUE,

  maker_checker_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
  maker_checker_actor_ne_submitter BOOLEAN NOT NULL DEFAULT TRUE,
  maker_checker_sla_hours          INTEGER NOT NULL DEFAULT 24,
  maker_checker_escalate_to_role   TEXT NOT NULL DEFAULT '',

  fail_fast                       BOOLEAN NOT NULL DEFAULT FALSE,

  schedule                        JSONB,

  submitted_by                    TEXT,
  rejection_reason                TEXT,
  created_by                      TEXT NOT NULL,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT templates_code_not_blank CHECK (btrim(template_code) <> ''),
  CONSTRAINT templates_name_not_blank CHECK (btrim(template_name) <> ''),
  CONSTRAINT templates_name_len CHECK (char_length(template_name) <= 120),
  CONSTRAINT templates_package_size_range CHECK (package_max_size_mb BETWEEN 1 AND 500),
  CONSTRAINT templates_package_rows_positive CHECK (package_max_rows IS NULL OR package_max_rows >= 1),
  CONSTRAINT templates_sla_range CHECK (maker_checker_sla_hours BETWEEN 1 AND 720),
  CONSTRAINT templates_rejection_when_rejected CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> ''),
  CONSTRAINT templates_schedule_is_object CHECK (schedule IS NULL OR jsonb_typeof(schedule) = 'object')
);

CREATE UNIQUE INDEX templates_code_uidx ON templates (template_code);
CREATE INDEX templates_process_idx ON templates (process_id);
CREATE INDEX templates_status_idx ON templates (status);
CREATE INDEX templates_inbox_idx ON templates (status) WHERE status = 'waitingForChecker';
CREATE INDEX templates_database_connection_idx ON templates (database_connection_id);

CREATE TRIGGER templates_set_updated_at
  BEFORE UPDATE ON templates
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN templates.template_code IS 'Server-generated human-readable code (e.g. TPL_A1B2C3D4).';
COMMENT ON COLUMN templates.version IS 'Semver. Patch bumps and status reverts to draft when an active template is edited.';
COMMENT ON COLUMN templates.schedule IS
  'Nullable TemplateScheduleConfig snapshot: {enabled, frequency, timeOfDay, dayOfWeek, dayOfMonth, cronExpression, pickup:{host,port,username,credentialRef,basePath}, filePattern, uploadFormat, autoApprove, lastRunAt, nextRunAt}. pickup.credentialRef is shape-only — never a live secret.';

-- ---- V1_0_38__create_template_fields.sql ----
-- V1_0_38__create_template_fields.sql
-- Purpose: Column-to-field mapping for a template's upload files (admin-api-contract.md §2.2 fields[]).

CREATE TABLE template_fields (
  field_id        TEXT PRIMARY KEY DEFAULT generate_id('fld'),
  template_id     TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  source_column   TEXT NOT NULL,
  target_field    TEXT NOT NULL,
  field_label     TEXT NOT NULL,
  field_type      VARCHAR(10) NOT NULL DEFAULT 'string' CHECK (field_type IN ('string','number','date','boolean')),
  required        BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order      INTEGER NOT NULL DEFAULT 0,

  CONSTRAINT template_fields_source_not_blank CHECK (btrim(source_column) <> ''),
  CONSTRAINT template_fields_target_not_blank CHECK (btrim(target_field) <> ''),
  CONSTRAINT template_fields_label_not_blank CHECK (btrim(field_label) <> '')
);

CREATE UNIQUE INDEX template_fields_target_uidx ON template_fields (template_id, lower(target_field));
CREATE UNIQUE INDEX template_fields_source_uidx ON template_fields (template_id, lower(source_column));
CREATE INDEX template_fields_template_idx ON template_fields (template_id, sort_order);

-- ---- V1_0_39__create_template_upload_formats.sql ----
-- V1_0_39__create_template_upload_formats.sql
-- Purpose: Per-template XLSX/CSV/JSON parse options — exactly one row per format key per
-- template, auto-seeded by a trigger on every templates INSERT (admin-api-contract.md §2.2
-- uploadFormats, §2.3 "seeded uploadFormats").

CREATE TABLE template_upload_formats (
  template_id       TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  format_key        VARCHAR(10) NOT NULL CHECK (format_key IN ('xlsx','csv','json')),
  enabled           BOOLEAN NOT NULL DEFAULT FALSE,
  max_size_mb       INTEGER NOT NULL,
  sheet_name        TEXT,
  delimiter         TEXT,
  charset           TEXT,
  header_row        INTEGER,
  root_array_path   TEXT,

  PRIMARY KEY (template_id, format_key),

  CONSTRAINT template_formats_size_range CHECK (max_size_mb BETWEEN 1 AND 500),
  CONSTRAINT template_formats_header_row_positive CHECK (header_row IS NULL OR header_row >= 1)
);

COMMENT ON TABLE template_upload_formats IS
  'Per-template XLSX / CSV / JSON parse options. At least one format must be enabled (enforced by the service layer, not a table CHECK).';

-- Seed the three format rows whenever a template is created, regardless of insert path — this is
-- an AFTER INSERT trigger on `templates`, so it fires for Hibernate-issued INSERTs too. The
-- service layer re-queries these rows after create() to include them in the response.
CREATE OR REPLACE FUNCTION seed_template_formats()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO template_upload_formats
    (template_id, format_key, enabled, max_size_mb, sheet_name, delimiter, charset, header_row, root_array_path)
  VALUES
    (NEW.template_id, 'xlsx', TRUE,  50, 'Data', NULL, NULL,    NULL, NULL),
    (NEW.template_id, 'csv',  TRUE,  50, NULL,   ',',  'UTF-8', 1,    NULL),
    (NEW.template_id, 'json', FALSE, 30, NULL,   NULL, NULL,    NULL, '$');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER templates_seed_formats
  AFTER INSERT ON templates
  FOR EACH ROW EXECUTE FUNCTION seed_template_formats();

-- ---- V1_0_40__create_template_pk_fields.sql ----
-- V1_0_40__create_template_pk_fields.sql
-- Purpose: Ordered data-load primary/composite key field list (admin-api-contract.md §2.2
-- dataLoad.primaryKeyFields).

CREATE TABLE template_pk_fields (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  target_field  TEXT NOT NULL,
  sort_order    INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (template_id, target_field)
);

CREATE INDEX template_pk_fields_order_idx ON template_pk_fields (template_id, sort_order);

-- ---- V1_0_41__create_template_sort_fields.sql ----
-- V1_0_41__create_template_sort_fields.sql
-- Purpose: Row sort order used when dataLoad.rowOrder = "sortByKey" (admin-api-contract.md §2.2
-- dataLoad.sortFields).

CREATE TABLE template_sort_fields (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  target_field  TEXT NOT NULL,
  direction     VARCHAR(4) NOT NULL DEFAULT 'asc' CHECK (direction IN ('asc','desc')),
  sort_order    INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (template_id, target_field)
);

CREATE INDEX template_sort_fields_order_idx ON template_sort_fields (template_id, sort_order);

-- ---- V1_0_42__create_template_checker_roles.sql ----
-- V1_0_42__create_template_checker_roles.sql
-- Purpose: Upload-level maker-checker role refs (admin-api-contract.md §2.4 makerChecker.checkerRoles).

CREATE TABLE template_checker_roles (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  role_ref      TEXT NOT NULL,

  PRIMARY KEY (template_id, role_ref),

  CONSTRAINT template_checker_roles_ref_not_blank CHECK (btrim(role_ref) <> '')
);

-- ---- V1_0_43__create_template_transformations.sql ----
-- V1_0_43__create_template_transformations.sql
-- Purpose: Field value transformations (recode mappings applied at load time)
-- (admin-api-contract.md §2.2 transformations[]).

CREATE TABLE template_transformations (
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  target_field  TEXT NOT NULL,
  mappings      JSONB NOT NULL DEFAULT '[]',
  sort_order    INTEGER NOT NULL DEFAULT 0,

  PRIMARY KEY (template_id, target_field),

  CONSTRAINT template_transformations_mappings_is_array CHECK (jsonb_typeof(mappings) = 'array')
);

COMMENT ON COLUMN template_transformations.mappings IS
  'Array of {from, to} value-recode pairs applied to target_field during load.';

-- ---- V1_0_44__create_template_validation_rules.sql ----
-- V1_0_44__create_template_validation_rules.sql
-- Purpose: Pre-upload / functional / master / transactional validation rules
-- (admin-api-contract.md §2.4 "Validation Rule Object").

CREATE TABLE template_validation_rules (
  rule_id               TEXT PRIMARY KEY DEFAULT generate_id('R'),
  template_id           TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  field                 TEXT NOT NULL DEFAULT '',
  rule_type             VARCHAR(20) NOT NULL CHECK (rule_type IN
                         ('FORMAT_REGEX','NULL_EMPTY','ENUM','DECIMAL_PRECISION','RANGE','DATE_FORMAT','FUNCTIONAL','MASTER_DATA','TRANSACTION')),
  severity              VARCHAR(10) NOT NULL DEFAULT 'ERROR' CHECK (severity IN ('ERROR','WARNING')),
  message               TEXT NOT NULL DEFAULT '',

  -- FORMAT_REGEX
  profile               TEXT,
  pattern               TEXT,
  format                TEXT,

  -- NULL_EMPTY
  required              BOOLEAN,
  reject_empty_string   BOOLEAN,
  reject_whitespace     BOOLEAN,

  -- ENUM
  allowed_values        TEXT[],
  case_insensitive      BOOLEAN,

  -- DECIMAL_PRECISION / RANGE
  decimal_places        INTEGER,
  delimiter             VARCHAR(1) CHECK (delimiter IS NULL OR delimiter IN ('.', ',', '''')),
  min_value              NUMERIC,
  max_value              NUMERIC,

  -- FUNCTIONAL
  expression             TEXT,
  formula_terms          JSONB,
  formula_operators      JSONB,

  -- TRANSACTION
  compare_operator        VARCHAR(4) CHECK (compare_operator IS NULL OR compare_operator IN ('lt','lte','gt','gte','eq')),
  group_by_field          TEXT,
  transaction_split       JSONB,

  -- Shared optional row filter (FUNCTIONAL / TRANSACTION)
  condition               JSONB,

  sort_order              INTEGER NOT NULL DEFAULT 0,

  CONSTRAINT template_rules_formula_terms_is_array CHECK (formula_terms IS NULL OR jsonb_typeof(formula_terms) = 'array'),
  CONSTRAINT template_rules_formula_operators_is_array CHECK (formula_operators IS NULL OR jsonb_typeof(formula_operators) = 'array'),
  CONSTRAINT template_rules_transaction_split_is_object CHECK (transaction_split IS NULL OR jsonb_typeof(transaction_split) = 'object'),
  CONSTRAINT template_rules_condition_is_object CHECK (condition IS NULL OR jsonb_typeof(condition) = 'object')
);

CREATE INDEX template_rules_template_idx ON template_validation_rules (template_id, sort_order);
CREATE INDEX template_rules_type_idx ON template_validation_rules (template_id, rule_type);

COMMENT ON TABLE template_validation_rules IS 'Optional validation rules. MASTER_DATA is reserved (coming soon; cannot be activated).';
COMMENT ON COLUMN template_validation_rules.transaction_split IS
  'TRANSACTION rules only: {splitField, branchAValue, branchBValue, amountField} — splits filtered/grouped rows into two categories and compares their summed amountField using compare_operator.';
COMMENT ON COLUMN template_validation_rules.condition IS
  'Optional row filter: {conditionField, conditionOperator, conditionValue}. conditionOperator in (equals, notEquals, in, notIn, empty, notEmpty).';
COMMENT ON COLUMN template_validation_rules.formula_terms IS
  'FUNCTIONAL formula_compare rules only: array of {kind:"field",field} | {kind:"constant",value,isPercent?}.';
COMMENT ON COLUMN template_validation_rules.formula_operators IS
  'FUNCTIONAL formula_compare rules only: array of add|subtract|multiply|divide, one fewer than formula_terms.';

-- ---- V1_0_45__create_template_version_snapshots.sql ----
-- V1_0_45__create_template_version_snapshots.sql
-- Purpose: Immutable, append-only template snapshots — one row per save (admin-api-contract.md §2.8/§2.9).

CREATE TABLE template_version_snapshots (
  snapshot_id   TEXT PRIMARY KEY DEFAULT generate_id('ver'),
  template_id   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE CASCADE,
  version       TEXT NOT NULL,
  snapshot      JSONB NOT NULL,
  captured_by   TEXT NOT NULL,
  captured_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT template_version_snapshots_is_object CHECK (jsonb_typeof(snapshot) = 'object')
);

CREATE INDEX template_version_snapshots_template_idx ON template_version_snapshots (template_id, captured_at DESC);
CREATE UNIQUE INDEX template_version_snapshots_version_uidx ON template_version_snapshots (template_id, version);

COMMENT ON TABLE template_version_snapshots IS
  'Append-only, one row per save: a full point-in-time copy of the template (fields, rules, formats, transformations, post-load action, schedule, etc.). Never UPDATE or DELETE.';

REVOKE UPDATE, DELETE ON template_version_snapshots FROM PUBLIC;

-- ---- V1_0_46__create_upload_roles.sql ----
-- V1_0_46__create_upload_roles.sql
-- Purpose: Upload roles that gate maker-user access to processes (admin-api-contract.md §3).

CREATE TABLE upload_roles (
  role_id           TEXT PRIMARY KEY DEFAULT generate_id('role'),
  role_name         TEXT NOT NULL,
  description       TEXT NOT NULL DEFAULT '',
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  created_by        TEXT NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_roles_name_not_blank CHECK (btrim(role_name) <> ''),
  CONSTRAINT upload_roles_name_len CHECK (char_length(role_name) <= 64),
  CONSTRAINT upload_roles_description_len CHECK (char_length(description) <= 500),
  CONSTRAINT upload_roles_rejection_when_rejected CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX upload_roles_name_ci_uidx ON upload_roles (lower(role_name));
CREATE INDEX upload_roles_status_idx ON upload_roles (status);
CREATE INDEX upload_roles_inbox_idx ON upload_roles (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER upload_roles_set_updated_at
  BEFORE UPDATE ON upload_roles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- V1_0_47__create_upload_role_processes.sql ----
-- V1_0_47__create_upload_role_processes.sql
-- Purpose: Which processes an upload role grants access to (admin-api-contract.md §3.3 processAccess).

CREATE TABLE upload_role_processes (
  role_id     TEXT NOT NULL REFERENCES upload_roles (role_id) ON DELETE CASCADE,
  process_id  TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,

  PRIMARY KEY (role_id, process_id)
);

CREATE INDEX upload_role_processes_process_idx ON upload_role_processes (process_id);

-- ---- V1_0_48__create_maker_users.sql ----
-- V1_0_48__create_maker_users.sql
-- Purpose: Batch upload operators — not the Maker Admin actor who owns this table's rows
-- (admin-api-contract.md §4).

CREATE TABLE maker_users (
  user_id           TEXT PRIMARY KEY DEFAULT generate_id('user'),
  username          TEXT NOT NULL,
  full_name         TEXT NOT NULL,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  status            VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','waitingForChecker','active','rejected')),
  submitted_by      TEXT,
  rejection_reason  TEXT,
  created_by        TEXT NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT maker_users_username_not_blank CHECK (btrim(username) <> ''),
  CONSTRAINT maker_users_username_len CHECK (char_length(username) <= 120),
  CONSTRAINT maker_users_full_name_not_blank CHECK (btrim(full_name) <> ''),
  CONSTRAINT maker_users_full_name_len CHECK (char_length(full_name) <= 120),
  CONSTRAINT maker_users_rejection_when_rejected CHECK (status <> 'rejected' OR btrim(coalesce(rejection_reason, '')) <> '')
);

CREATE UNIQUE INDEX maker_users_username_ci_uidx ON maker_users (lower(username));
CREATE INDEX maker_users_status_idx ON maker_users (status);
CREATE INDEX maker_users_inbox_idx ON maker_users (status) WHERE status = 'waitingForChecker';

CREATE TRIGGER maker_users_set_updated_at
  BEFORE UPDATE ON maker_users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- V1_0_49__create_maker_user_roles.sql ----
-- V1_0_49__create_maker_user_roles.sql
-- Purpose: Which upload roles a maker user holds (admin-api-contract.md §4.2 roleIds).

CREATE TABLE maker_user_roles (
  user_id   TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE CASCADE,
  role_id   TEXT NOT NULL REFERENCES upload_roles (role_id) ON DELETE RESTRICT,

  PRIMARY KEY (user_id, role_id)
);

CREATE INDEX maker_user_roles_role_idx ON maker_user_roles (role_id);

-- ---- V1_0_50__create_audit_events.sql ----
-- V1_0_50__create_audit_events.sql
-- Purpose: Append-only admin activity log (admin-api-contract.md §9). Every state mutation across
-- every resource appends a row here via AuditEventService.

CREATE TABLE audit_events (
  event_id        TEXT PRIMARY KEY DEFAULT generate_id('evt'),
  event_code      TEXT NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_id        TEXT NOT NULL,
  process_id      TEXT,
  template_code   TEXT,
  outcome         VARCHAR(10) NOT NULL CHECK (outcome IN ('SUCCESS','FAILURE','INFO')),
  summary         TEXT NOT NULL,

  CONSTRAINT audit_events_code_not_blank CHECK (btrim(event_code) <> ''),
  CONSTRAINT audit_events_summary_not_blank CHECK (btrim(summary) <> '')
);

CREATE INDEX audit_events_occurred_idx ON audit_events (occurred_at DESC);
CREATE INDEX audit_events_actor_idx ON audit_events (actor_id);
CREATE INDEX audit_events_process_idx ON audit_events (process_id);
CREATE INDEX audit_events_code_idx ON audit_events (event_code);
CREATE INDEX audit_events_outcome_idx ON audit_events (outcome);

COMMENT ON TABLE audit_events IS 'Append-only admin activity log. Do not UPDATE or DELETE rows.';

REVOKE UPDATE, DELETE ON audit_events FROM PUBLIC;

-- ---- V1_0_51__create_v_checker_inbox.sql ----
-- V1_0_51__create_v_checker_inbox.sql
-- Purpose: Checker inbox — derived view, not a table (admin-api-contract.md §8). Pending Maker
-- submissions across every governed entity. The service layer must hide rows where
-- submitted_by = current actor (four-eyes); process_id_ref is populated for templates only, to
-- build a review link back to their parent process.

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
WHERE a.status = 'waitingForChecker';

COMMENT ON VIEW v_checker_inbox IS
  'Pending Maker submissions across all governed entities. API must hide rows where submitted_by = current actor (four-eyes). process_id_ref is populated for templates only, to build a review link back to their parent process.';

-- ---- V1_0_52__create_upload_attempts.sql ----
-- V1_0_52__create_upload_attempts.sql
-- Purpose: Upload-runtime schema (one row per file a maker uploads for validation), ported for
-- referential completeness only. Belongs to the separate upload-operator API
-- (upload-api-contract.md), which has not been provided yet — no Java entity/service/controller
-- layer is built for this table in this pass.

CREATE TABLE upload_attempts (
  upload_attempt_id             TEXT PRIMARY KEY DEFAULT generate_id('upl'),
  process_id                    TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  process_name                  TEXT NOT NULL,
  template_id                   TEXT NOT NULL REFERENCES templates (template_id) ON DELETE RESTRICT,
  template_code                 TEXT NOT NULL,
  template_version               TEXT NOT NULL,
  maker_user_id                  TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  original_filename              TEXT NOT NULL,
  upload_format                  VARCHAR(10) NOT NULL CHECK (upload_format IN ('xlsx','csv','json')),
  file_size_bytes                BIGINT NOT NULL,
  original_file_checksum_sha256  TEXT NOT NULL,
  status                         VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED' CHECK (status IN
                                  ('ACCEPTED','VALIDATING','READY_FOR_DECISION','CONTINUED','REUPLOADED','TIMED_OUT','ABORTED')),
  summary                        JSONB,
  issues                         JSONB NOT NULL DEFAULT '[]',
  decision                       VARCHAR(10) CHECK (decision IS NULL OR decision IN ('PROCEED','REUPLOAD')),
  decided_at                     TIMESTAMPTZ,
  maker_checker_enabled          BOOLEAN NOT NULL,
  validations_enabled            BOOLEAN NOT NULL,
  created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_attempts_summary_is_object CHECK (summary IS NULL OR jsonb_typeof(summary) = 'object'),
  CONSTRAINT upload_attempts_issues_is_array CHECK (jsonb_typeof(issues) = 'array')
);

CREATE INDEX upload_attempts_process_idx ON upload_attempts (process_id);
CREATE INDEX upload_attempts_maker_idx ON upload_attempts (maker_user_id, created_at DESC);
CREATE INDEX upload_attempts_status_idx ON upload_attempts (status);

-- Enforce "one active (non-terminal) attempt per process" at the DB level too.
CREATE UNIQUE INDEX upload_attempts_one_active_per_process_uidx ON upload_attempts (process_id)
  WHERE status NOT IN ('CONTINUED', 'REUPLOADED', 'TIMED_OUT', 'ABORTED');

CREATE TRIGGER upload_attempts_set_updated_at
  BEFORE UPDATE ON upload_attempts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE upload_attempts IS
  'Belongs to the separate upload-operator API (upload-api-contract.md, not yet provided) — schema included for referential completeness only; no service/controller layer in this pass.';

-- ---- V1_0_53__create_upload_submissions.sql ----
-- V1_0_53__create_upload_submissions.sql
-- Purpose: Upload-runtime schema (handed off to a Checker when maker-checker is enabled), ported
-- for referential completeness only — see V1_0_52 header.

CREATE TABLE upload_submissions (
  submission_id                  TEXT PRIMARY KEY DEFAULT generate_id('sub'),
  upload_attempt_id              TEXT NOT NULL UNIQUE REFERENCES upload_attempts (upload_attempt_id) ON DELETE RESTRICT,
  process_id                     TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  process_name                   TEXT NOT NULL,
  template_code                  TEXT NOT NULL,
  template_version                TEXT NOT NULL,
  maker_user_id                   TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  maker_display_name              TEXT NOT NULL,
  pending_object_key              TEXT NOT NULL,
  storage_provider                 VARCHAR(20) NOT NULL CHECK (storage_provider IN ('AWS_S3','AZURE_BLOB','GCS','ON_PREM')),
  summary                          JSONB NOT NULL,
  issues                           JSONB NOT NULL DEFAULT '[]',
  original_file_checksum_sha256   TEXT NOT NULL,
  status                           VARCHAR(20) NOT NULL DEFAULT 'WAITING_FOR_CHECKER'
                                    CHECK (status IN ('WAITING_FOR_CHECKER','ACCEPTED','REJECTED','EXPIRED')),
  checker_user_id                  TEXT REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  review_reason                    TEXT,
  expires_at                       TIMESTAMPTZ NOT NULL,
  created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_submissions_summary_is_object CHECK (jsonb_typeof(summary) = 'object'),
  CONSTRAINT upload_submissions_issues_is_array CHECK (jsonb_typeof(issues) = 'array')
);

CREATE INDEX upload_submissions_maker_idx ON upload_submissions (maker_user_id, created_at DESC);
CREATE INDEX upload_submissions_checker_inbox_idx ON upload_submissions (status) WHERE status = 'WAITING_FOR_CHECKER';

CREATE TRIGGER upload_submissions_set_updated_at
  BEFORE UPDATE ON upload_submissions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN upload_submissions.checker_user_id IS
  'References maker_users because the demo model shares one user table for both maker and checker upload operators (distinct from the Maker/Checker Admin actors).';
COMMENT ON TABLE upload_submissions IS
  'Belongs to the separate upload-operator API (upload-api-contract.md, not yet provided) — schema included for referential completeness only; no service/controller layer in this pass.';

-- ---- V1_0_54__create_upload_jobs.sql ----
-- V1_0_54__create_upload_jobs.sql
-- Purpose: Upload-runtime schema (post-load-action delivery job), ported for referential
-- completeness only — see V1_0_52 header.

CREATE TABLE upload_jobs (
  job_id                          TEXT PRIMARY KEY DEFAULT generate_id('job'),
  process_code                    TEXT NOT NULL,
  process_name                    TEXT NOT NULL,
  template_code                   TEXT NOT NULL,
  template_version                 TEXT NOT NULL,
  maker_user_id                    TEXT NOT NULL REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  checker_user_id                   TEXT REFERENCES maker_users (user_id) ON DELETE RESTRICT,
  submission_id                     TEXT REFERENCES upload_submissions (submission_id) ON DELETE RESTRICT,
  upload_attempt_id                 TEXT NOT NULL REFERENCES upload_attempts (upload_attempt_id) ON DELETE RESTRICT,
  upload_format                     VARCHAR(10) NOT NULL CHECK (upload_format IN ('xlsx','csv','json')),
  total_records                     INTEGER NOT NULL DEFAULT 0,
  passed_records                    INTEGER NOT NULL DEFAULT 0,
  failed_records                    INTEGER NOT NULL DEFAULT 0,
  warning_records                   INTEGER NOT NULL DEFAULT 0,
  completed_file_key                 TEXT NOT NULL,
  original_object_key                 TEXT,
  storage_provider                    VARCHAR(20) NOT NULL CHECK (storage_provider IN ('AWS_S3','AZURE_BLOB','GCS','ON_PREM')),
  maker_checker_enabled                BOOLEAN NOT NULL,
  original_file_checksum_sha256        TEXT NOT NULL,
  status                                VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED')),
  queue_job_ref                         TEXT,
  created_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX upload_jobs_maker_idx ON upload_jobs (maker_user_id, created_at DESC);
CREATE INDEX upload_jobs_status_idx ON upload_jobs (status);

CREATE TRIGGER upload_jobs_set_updated_at
  BEFORE UPDATE ON upload_jobs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN upload_jobs.queue_job_ref IS
  'Reference into the actual delivery mechanism (Kafka message key, DB-writer batch id) named by the template''s post_load_action — not resolved further here.';
COMMENT ON TABLE upload_jobs IS
  'Belongs to the separate upload-operator API (upload-api-contract.md, not yet provided) — schema included for referential completeness only; no service/controller layer in this pass.';

-- ---- V1_0_55__add_s3_fields_to_storage_configs.sql ----
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

-- ---- V1_0_56__create_upload_files.sql ----
-- V1_0_56__create_upload_files.sql
-- Purpose: Tracks each raw file upload to the interim object store (MakerUploadController /
-- S3UploadServiceImpl). checksum_sha256 lets a repeat upload of the same file for the same
-- template be rejected before it reaches S3; status lets callers report pending/inProgress/
-- completed/failed counts. Distinct from upload_attempts (V1_0_52), which is the not-yet-built
-- validation-pipeline table from upload-api-contract.md — this one tracks the storage step only.

CREATE TABLE upload_files (
  upload_id          TEXT PRIMARY KEY DEFAULT generate_id('rawupl'),
  process_id         TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  template_id        TEXT NOT NULL REFERENCES templates (template_id) ON DELETE RESTRICT,
  original_filename  TEXT NOT NULL,
  checksum_sha256    TEXT NOT NULL,
  file_size_bytes    BIGINT NOT NULL,
  content_type       TEXT,
  s3_bucket          TEXT,
  s3_key             TEXT,
  e_tag              TEXT,
  status             VARCHAR(12) NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending','inProgress','completed','failed')),
  uploaded_by        TEXT NOT NULL,
  error_message      TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT upload_files_filename_not_blank CHECK (btrim(original_filename) <> ''),
  CONSTRAINT upload_files_checksum_not_blank CHECK (btrim(checksum_sha256) <> '')
);

-- Application-level duplicate check races two concurrent uploads of the same file; this is the
-- DB-level backstop — at most one non-failed row per (template, checksum).
CREATE UNIQUE INDEX upload_files_dedup_uidx ON upload_files (template_id, checksum_sha256)
  WHERE status <> 'failed';

CREATE INDEX upload_files_status_idx ON upload_files (status);
CREATE INDEX upload_files_process_idx ON upload_files (process_id);
CREATE INDEX upload_files_template_idx ON upload_files (template_id);

CREATE TRIGGER upload_files_set_updated_at
  BEFORE UPDATE ON upload_files
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE upload_files IS
  'One row per raw file upload attempt to the interim object store. checksum_sha256 blocks duplicate uploads of the same file for the same template (see upload_files_dedup_uidx); status tracks pending/inProgress/completed/failed for count reporting.';

-- ---- V1_0_57__relax_template_version_snapshots_uniqueness.sql ----
-- V1_0_57__relax_template_version_snapshots_uniqueness.sql
-- Purpose: The table is documented as "one row per save", but the original UNIQUE(template_id,
-- version) index actually enforced "one row per version" — silently dropping every snapshot after
-- the first at a given version, including the one that should be captured at accept() time (the
-- template's version number does not change between submit and accept, so the accept-time save
-- was always rejected as a duplicate before it could be written). Replace the unique index with a
-- plain one: same query performance for template_id+version lookups, but multiple saves at the
-- same version (e.g. the initial draft, and later the moment it was approved) can each get their
-- own row, as the table's own append-only "one row per save" contract always intended.

DROP INDEX template_version_snapshots_version_uidx;
CREATE INDEX template_version_snapshots_version_idx ON template_version_snapshots (template_id, version);

-- ---- V1_0_58__clarify_storage_configs_hostname_port_optional.sql ----
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

-- ---- V1_0_59__create_audit_event_catalogue.sql ----
-- V1_0_59__create_audit_event_catalogue.sql
-- Purpose: static reference table listing every audit_events.event_code this system is allowed to
-- record — both the admin config-mutation codes already in production use (admin-api-contract.md
-- §9/§12.6) and the full upload-pipeline event catalogue from the Solution Design (SD §12.3),
-- which nothing emits yet (see category = 'PIPELINE'; the pipeline stages that would emit most of
-- them — validation, checker review, job creation, queue push — have no real backend layer yet,
-- V1_0_52..54's comments). Cataloguing them now means the schema is ready the moment each stage is
-- built, and V1_0_60 adds a foreign key so audit_events can never contain an uncatalogued code.
--
-- S3_PENDING_WRITE_*/S3_PROMOTE_*/S3_WRITE_* suffixes are this migration's own choice: SD §12.3
-- allocates event numbers 17-19, 23-24, and 25 to these families but only spells out the "_*"
-- prefix, not the individual suffixes. STARTED/COMPLETED/FAILED (3), COMPLETED/FAILED (2), and
-- COMPLETED (1) were chosen to match that count and this codebase's existing UploadFileStatus
-- vocabulary (see UploadS3Worker) — revisit if the eventual upload-api-contract.md names them
-- differently.

CREATE TABLE audit_event_catalogue (
  event_code    TEXT PRIMARY KEY,
  category      TEXT NOT NULL CHECK (category IN ('ADMIN', 'PIPELINE')),
  description   TEXT NOT NULL,
  sd_reference  TEXT,

  CONSTRAINT audit_event_catalogue_code_not_blank CHECK (btrim(event_code) <> '')
);

COMMENT ON TABLE audit_event_catalogue IS
  'Static reference data — the complete set of event_code values audit_events is allowed to '
  'contain. Not admin-editable; changes ship as a new migration, same as the codes themselves.';

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  -- Admin config-mutation events — already recorded by every *ServiceImpl's maker-checker methods
  -- (admin-api-contract.md §9/§12.6). One row per event actually emitted in code today.
  ('ADMIN_ROLE_CREATED',          'ADMIN', 'Upload role created (draft)',                      'admin-api-contract.md §9'),
  ('ADMIN_ROLE_UPDATED',          'ADMIN', 'Upload role edited',                                'admin-api-contract.md §9'),
  ('ADMIN_ROLE_SUBMITTED',        'ADMIN', 'Upload role submitted for Checker Admin approval',  'admin-api-contract.md §9'),
  ('ADMIN_ROLE_ACTIVATED',        'ADMIN', 'Upload role approved and activated',                'admin-api-contract.md §9'),
  ('ADMIN_ROLE_REJECTED',         'ADMIN', 'Upload role rejected by Checker Admin',              'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_CREATED',      'ADMIN', 'Database connection created (draft)',                'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_UPDATED',      'ADMIN', 'Database connection edited',                         'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_SUBMITTED',    'ADMIN', 'Database connection submitted for approval',         'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_ACTIVATED',    'ADMIN', 'Database connection approved and activated',         'admin-api-contract.md §9'),
  ('ADMIN_DATABASE_REJECTED',     'ADMIN', 'Database connection rejected by Checker Admin',      'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_CREATED',    'ADMIN', 'API configuration created (draft)',                  'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_UPDATED',    'ADMIN', 'API configuration edited',                           'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_SUBMITTED',  'ADMIN', 'API configuration submitted for approval',           'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_ACTIVATED',  'ADMIN', 'API configuration approved and activated',           'admin-api-contract.md §9'),
  ('ADMIN_API_CONFIG_REJECTED',   'ADMIN', 'API configuration rejected by Checker Admin',        'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_CREATED',      'ADMIN', 'Template created (draft)',                           'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_UPDATED',      'ADMIN', 'Template edited',                                    'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_SUBMITTED',    'ADMIN', 'Template submitted for approval',                    'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_ACTIVATED',    'ADMIN', 'Template approved and activated',                    'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_REJECTED',     'ADMIN', 'Template rejected by Checker Admin',                 'admin-api-contract.md §9'),
  ('ADMIN_TEMPLATE_CLONED',       'ADMIN', 'Template cloned into a new draft version',           'admin-api-contract.md §9'),
  ('ADMIN_USER_CREATED',          'ADMIN', 'Maker user created (draft)',                         'admin-api-contract.md §9'),
  ('ADMIN_USER_UPDATED',          'ADMIN', 'Maker user edited',                                  'admin-api-contract.md §9'),
  ('ADMIN_USER_SUBMITTED',        'ADMIN', 'Maker user submitted for approval',                  'admin-api-contract.md §9'),
  ('ADMIN_USER_ACTIVATED',        'ADMIN', 'Maker user approved and activated',                  'admin-api-contract.md §9'),
  ('ADMIN_USER_REJECTED',         'ADMIN', 'Maker user rejected by Checker Admin',               'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_CREATED',       'ADMIN', 'Process created (draft)',                            'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_UPDATED',       'ADMIN', 'Process edited',                                     'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_SUBMITTED',     'ADMIN', 'Process submitted for approval',                     'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_ACTIVATED',     'ADMIN', 'Process approved and activated',                     'admin-api-contract.md §9'),
  ('ADMIN_PROCESS_REJECTED',      'ADMIN', 'Process rejected by Checker Admin',                  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_CREATED',       'ADMIN', 'Interim storage connection created (draft)',         'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_UPDATED',       'ADMIN', 'Interim storage connection edited',                  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_SUBMITTED',     'ADMIN', 'Interim storage connection submitted for approval',  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_ACTIVATED',     'ADMIN', 'Interim storage connection approved and activated',  'admin-api-contract.md §9'),
  ('ADMIN_STORAGE_REJECTED',      'ADMIN', 'Interim storage connection rejected by Checker Admin','admin-api-contract.md §9'),

  -- Upload-pipeline events — the full normative catalogue from SD §12.3, in event-number order.
  -- Only FILE_RECEIVED and FILE_REJECTED are emitted today (S3UploadServiceImpl, wired in this
  -- pass); everything else is catalogued ahead of the backend layer that will emit it.
  ('AUTH_OK',                     'PIPELINE', 'Maker/Checker authenticated',                            'SD §12.3 #1'),
  ('PROCESS_SELECTED',            'PIPELINE', 'Maker selected an upload process',                       'SD §12.3 #2'),
  ('TEMPLATE_SELECTED',           'PIPELINE', 'Active template resolved for the process',               'SD §12.3 #3'),
  ('TEMPLATE_DOWNLOADED',         'PIPELINE', 'Maker downloaded the blank template',                    'SD §12.3 #4'),
  ('FILE_RECEIVED',               'PIPELINE', 'Raw file accepted, SHA-256 checksum recorded',           'SD §12.3 #5'),
  ('FILE_REJECTED',               'PIPELINE', 'Raw file rejected (format/size/empty/duplicate checksum)','SD §12.3 #6'),
  ('CONCURRENT_UPLOAD_REJECTED',  'PIPELINE', 'Second concurrent upload on the same process rejected',  'SD §12.3 #6a'),
  ('VALIDATION_STARTED',          'PIPELINE', 'Chunked validation started',                              'SD §12.3 #8'),
  ('VALIDATION_CHUNK_DONE',       'PIPELINE', 'One validation chunk completed (optional, high-volume)',  'SD §12.3 #9'),
  ('VALIDATION_COMPLETED',        'PIPELINE', 'Validation run completed, results available',            'SD §12.3 #10'),
  ('VALIDATION_SKIPPED',          'PIPELINE', 'Validations skipped for this upload (audit reason)',     'SD §12.3 #11'),
  ('SESSION_TIMED_OUT',           'PIPELINE', 'Validation Service session timed out',                   'SD §12.3 #11a'),
  ('DECISION_REUPLOAD',           'PIPELINE', 'Maker chose to re-upload the entire file',               'SD §12.3 #15'),
  ('DECISION_PROCEED',            'PIPELINE', 'Maker chose to continue with successful records',        'SD §12.3 #16'),
  ('S3_PENDING_WRITE_STARTED',    'PIPELINE', 'Write to the pending/holding location started',          'SD §12.3 #17-19'),
  ('S3_PENDING_WRITE_COMPLETED',  'PIPELINE', 'Write to the pending/holding location completed',        'SD §12.3 #17-19'),
  ('S3_PENDING_WRITE_FAILED',     'PIPELINE', 'Write to the pending/holding location failed',           'SD §12.3 #17-19'),
  ('CHECKER_SUBMITTED',           'PIPELINE', 'Submission entered the Checker Batch Upload inbox',      'SD §12.3 #20'),
  ('CHECKER_APPROVED',            'PIPELINE', 'Checker accepted the submission',                        'SD §12.3 #21'),
  ('CHECKER_REJECTED',            'PIPELINE', 'Checker rejected the submission, or it expired',         'SD §12.3 #22'),
  ('S3_PROMOTE_COMPLETED',        'PIPELINE', 'Promoted pending -> final/completed location',           'SD §12.3 #23-24'),
  ('S3_PROMOTE_FAILED',           'PIPELINE', 'Promotion pending -> final/completed location failed',   'SD §12.3 #23-24'),
  ('S3_WRITE_COMPLETED',          'PIPELINE', 'Dual-control-off direct write to the completed location','SD §12.3 #25'),
  ('JOB_METADATA_CREATED',        'PIPELINE', 'Downstream job metadata row created',                    'SD §12.3 #26'),
  ('ENQUEUE_PUSHED',              'PIPELINE', 'Job pushed to the downstream queue',                      'SD §12.3 #27'),
  ('ENQUEUE_FAILED',              'PIPELINE', 'Push to the downstream queue failed',                     'SD §12.3 #28'),
  ('SESSION_FINALIZED',           'PIPELINE', 'Validation Service released this upload''s working data', 'SD §12.3 #29');

-- ---- V1_0_60__extend_audit_events_for_pipeline_trail.sql ----
-- V1_0_60__extend_audit_events_for_pipeline_trail.sql
-- Purpose: bring audit_events up to the Solution Design §12.4 event schema so it can carry
-- upload-pipeline events (not just admin config mutations) once each pipeline stage is real.
-- All new columns are nullable and additive — every existing ADMIN_* row and call site keeps
-- working unchanged; AuditEventService.record(...) keeps its original 6-arg overload for them.
--
-- Deliberately NOT matching SD §12.4 literally on two points, to stay consistent with this
-- codebase's existing conventions rather than the document's illustrative example:
--   - IDs stay TEXT + generate_id(prefix) (as event_id already is), not UUID.
--   - template_version stays TEXT ("1.0.1"), matching templates/upload_attempts, not an integer.
-- upload_attempt_id/submission_id/job_id are plain TEXT with no FK to upload_attempts/submissions/
-- jobs: those tables exist for referential completeness only (V1_0_52-54) with no service layer
-- yet, so nothing will ever populate them for real until that layer is built.

ALTER TABLE audit_events
  ADD COLUMN trace_id          TEXT,
  ADD COLUMN upload_attempt_id TEXT,
  ADD COLUMN submission_id     TEXT,
  ADD COLUMN job_id            TEXT,
  ADD COLUMN actor_roles       JSONB,
  ADD COLUMN template_version  TEXT,
  ADD COLUMN payload           JSONB,
  ADD COLUMN prev_event_id     TEXT REFERENCES audit_events (event_id),
  ADD CONSTRAINT audit_events_payload_is_object
    CHECK (payload IS NULL OR jsonb_typeof(payload) = 'object'),
  ADD CONSTRAINT audit_events_actor_roles_is_array
    CHECK (actor_roles IS NULL OR jsonb_typeof(actor_roles) = 'array'),
  ADD CONSTRAINT audit_events_event_code_fkey
    FOREIGN KEY (event_code) REFERENCES audit_event_catalogue (event_code);

COMMENT ON COLUMN audit_events.trace_id IS
  'End-to-end correlation id for one maker action (SD §12.2). Caller-generated per action, not '
  'per row — every audit_event emitted while handling that action shares the same trace_id.';
COMMENT ON COLUMN audit_events.prev_event_id IS
  'Previous event in the same upload_attempt_id chain, for tamper-evident ordering (SD §12.4). '
  'NULL for the first event in a chain.';

CREATE INDEX audit_events_trace_idx ON audit_events (trace_id, occurred_at) WHERE trace_id IS NOT NULL;
CREATE INDEX audit_events_attempt_idx ON audit_events (upload_attempt_id, occurred_at) WHERE upload_attempt_id IS NOT NULL;
CREATE INDEX audit_events_job_idx ON audit_events (job_id) WHERE job_id IS NOT NULL;

-- ---- V1_0_61__add_job_id_to_upload_files.sql ----
-- V1_0_61__add_job_id_to_upload_files.sql
-- Purpose: give UploadS3Worker a job id to hand back once the raw S3 PUT completes, so
-- diy-upload-web can key its data-validation-topic Kafka batches on something stable.
--
-- Deliberately NOT the same concept as upload_jobs.job_id (V1_0_54): that table is the
-- post-load-action delivery job for the not-yet-built validation/checker/S3-promote pipeline
-- (JOB_METADATA_CREATED is Solution Design §12.3 event #26, which fires after checker approval
-- and S3 promote — events #20-25) and most of its NOT NULL columns (upload_attempt_id,
-- total_records, completed_file_key, maker_checker_enabled, ...) have no data yet at raw-upload
-- time. This column is a lighter, earlier id scoped to upload_files only; when the real pipeline
-- lands, reconciling the two is a job for that work, not this one.

ALTER TABLE upload_files ADD COLUMN job_id TEXT;

CREATE INDEX upload_files_job_idx ON upload_files (job_id) WHERE job_id IS NOT NULL;

COMMENT ON COLUMN upload_files.job_id IS
  'Set once the S3 PUT completes (UploadS3Worker). Not the same row/concept as upload_jobs.job_id — see this migration''s header.';

-- ---- V1_0_62__add_config_locked_at_to_processes.sql ----
-- V1_0_62__add_config_locked_at_to_processes.sql
-- Purpose: timestamp a process's config_locked flag was set, so a scheduled reaper can force-
-- release a lock that's been held longer than the configured stale-lock timeout (e.g. because
-- validation-service crashed mid-batch and never published its completion event). Nullable and
-- additive — existing rows are unaffected.

ALTER TABLE processes
  ADD COLUMN config_locked_at TIMESTAMPTZ;

COMMENT ON COLUMN processes.config_locked_at IS
  'When config_locked was last set to true. NULL when not locked. Used by the stale-lock reaper '
  '(diy.upload.stale-lock-timeout-minutes) to force-release a lock nothing ever cleared.';

-- ---- V1_0_63__create_batch_upload_results.sql ----
-- V1_0_63__create_batch_upload_results.sql
-- Purpose: local copy of validation-service's per-batch outcome, pulled once when its
-- batch-validation-completed Kafka event arrives (BatchValidationCompletedListener), so
-- diy-upload-web can show row-wise results without ever calling validation-service directly.
-- One batch_upload_results row per batchId (summary/counts); one batch_upload_result_rows row per
-- failed row, mirroring validation-service's own batch_upload_row shape.

CREATE TABLE batch_upload_results (
  batch_id              UUID PRIMARY KEY,
  process_id            TEXT NOT NULL REFERENCES processes (process_id) ON DELETE RESTRICT,
  template_id           TEXT NOT NULL REFERENCES templates (template_id) ON DELETE RESTRICT,
  status                VARCHAR(20) NOT NULL,
  total_rows_received   INTEGER NOT NULL DEFAULT 0,
  passed_count          INTEGER NOT NULL DEFAULT 0,
  failed_count          INTEGER NOT NULL DEFAULT 0,
  received_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE batch_upload_results IS
  'One row per batch, populated once from validation-service''s completion event and REST '
  'failed-rows pull — not written by any other path.';

CREATE TABLE batch_upload_result_rows (
  id            TEXT PRIMARY KEY DEFAULT generate_id('bres'),
  batch_id      UUID NOT NULL REFERENCES batch_upload_results (batch_id) ON DELETE CASCADE,
  row_number    INTEGER NOT NULL,
  -- raw row data as validation-service returned it, keyed by field name
  row_data      JSONB NOT NULL,
  -- list of {field, ruleType, errorMessage} as validation-service returned it
  errors        JSONB NOT NULL
);

-- Paginated row listing for the results endpoint, in original row order
CREATE INDEX idx_batch_upload_result_rows_batch_row_number ON batch_upload_result_rows (batch_id, row_number);

-- ---- V1_0_64__add_warning_count_to_batch_upload_results.sql ----
-- V1_0_64__add_warning_count_to_batch_upload_results.sql
-- Purpose: validation-service now distinguishes ERROR vs WARNING severity per rule failure — this
-- carries the batch-level warning count through the completion event into the local results copy
-- (BatchValidationCompletedListener), alongside the existing passed/failed counts.

ALTER TABLE batch_upload_results
    ADD COLUMN warning_count INTEGER NOT NULL DEFAULT 0;

-- ---- V1_1_0__seed_admin_user.sql ----
-- V1_1_0__seed_admin_user.sql
-- Purpose: One administrator per tenant database, so a freshly onboarded tenant has somebody
-- who can sign in. Applied per tenant by TenantProvisioningService (db/tenant location).
--
-- password is NULL, and must stay NULL. The credential belongs to the tenant's Keycloak realm,
-- which issues a temporary one and forces a change on first sign-in. A hash here would be a
-- second credential that nothing rotates and that would still work after the realm account was
-- disabled. This file previously carried BCrypt hashes of 'admin123' and 'user123' — the same
-- two in every tenant of every project cloned from this template.
--
-- Seeding a row here rather than only in Keycloak keeps the SQL as the single place a starting
-- user is declared, with the realm following it, instead of the two being maintained separately.
--
-- Ids are explicit and stable: created_by/updated_by point at users by foreign key, so letting
-- each tenant database invent its own would make the same administrator a different id per
-- tenant, and nothing that compares two tenants would line up. They are also deliberately
-- different from the admin-side ids in db/migration/V1_1_0, so the two are never confused.

-- Tenant administrator. created_by points at itself: it is the first row, and there is nobody
-- else for it to point at.
INSERT INTO users (
    id, username, first_name, last_name, email_id, mobile_number,
    send_activation_email, send_activation_sms,
    password, password_creation_date, password_expiry_days, password_invalid_attempts,
    status, created_by, created_at
) VALUES (
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    'tenant_admin', 'Tenant', 'Admin',
    'tenant_admin@example.com', '9876543210',
    0, 0,
    NULL, NULL, 90, 0, 1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- A second, non-administrator account, so the role and access-control model has two users to
-- tell apart. Delete it if the product has no such distinction.
INSERT INTO users (
    id, username, first_name, last_name, email_id, mobile_number,
    send_activation_email, send_activation_sms,
    password, password_creation_date, password_expiry_days, password_invalid_attempts,
    status, created_by, created_at
) VALUES (
    '2d9ef613-8e52-4ef1-9079-5d4c3871e189',
    'tenant_user', 'Tenant', 'User',
    'tenant_user@example.com', '9876543200',
    0, 0,
    NULL, NULL, 90, 0, 1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- ---- V1_1_1__seed_roles.sql ----
-- V1_1_1__seed_roles.sql
-- Purpose: Seed initial roles in every tenant database.
-- role_type: 1 = User role, 2 = API Client role

INSERT INTO roles (id, role_type, name, description, status, created_by, created_at) VALUES
    ('6bcd12b5-5a40-4786-95b3-49c18c7d3956', 1, 'ROLE_TENANT_ADMIN',
     'Administrator role for tenant with all privileges', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP),
    ('c8e22c9f-25c8-4939-b107-6a77dec9c354', 1, 'ROLE_TENANT_USER',
     'Standard user role for tenant with limited privileges', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP),
    ('c8e22c9f-18c8-1239-b107-6a77dec9c354', 2, 'ROLE_TENANT_API_CLIENT',
     'API Client role for tenant with service-to-service access', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- Assign ROLE_TENANT_ADMIN to tenant_admin
INSERT INTO api_client_user_roles (id, user_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
        '6bcd12b5-5a40-4786-95b3-49c18c7d3956',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- Assign ROLE_TENANT_USER to tenant_user
INSERT INTO api_client_user_roles (id, user_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        '2d9ef613-8e52-4ef1-9079-5d4c3871e189',
        'c8e22c9f-25c8-4939-b107-6a77dec9c354',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- ---- V1_1_2__seed_api_clients.sql ----
-- V1_1_2__seed_api_clients.sql
-- Purpose: Seed the default API client(s) in every tenant database.
-- Uses ${tenant_code} placeholder (resolved per-tenant by TenantProvisioningService).
-- client_secret BCrypt hashes use 'client123' — replace in production.

-- Internal service-to-service API client (client_credentials grant via Keycloak)
INSERT INTO api_clients (
    id, client_id, client_secret, name, email_id, description, status, created_by, created_at
) VALUES (
    'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b6',
    'tenant_default_${tenant_code}_client',
    '$2a$10$MmnIXOJrjFiBs.bOgBMKoOeSFAHfqE2I6dkDL8cCLTw2IWC/UODiu', -- client123
    'Default ${tenant_code} API Client',
    'tenant_default_${tenant_code}_client@example.com',
    'Default internal API client for tenant ${tenant_code} service-to-service access',
    1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- Customer-facing public API client
INSERT INTO api_clients (
    id, client_id, client_secret, name, email_id, description, status, created_by, created_at
) VALUES (
    'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b7',
    'tenant_default_${tenant_code}_customer_client',
    '$2a$10$aNeRPicjYJER28B4cymPHebn7LeY1E07hY13Nkgl.4HL21MZSy5c.', -- TenantDef@ultQcpClient123
    'Default ${tenant_code} Customer API Client',
    'tenant_default_${tenant_code}_customer_client@example.com',
    'Default customer-facing API client for tenant ${tenant_code}',
    1,
    '2d9ef613-8e52-4ef1-9079-5d4c3871e188',
    CURRENT_TIMESTAMP
);

-- Assign ROLE_TENANT_API_CLIENT to internal client
INSERT INTO api_client_user_roles (id, api_client_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b6',
        'c8e22c9f-18c8-1239-b107-6a77dec9c354',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- Assign ROLE_TENANT_API_CLIENT to customer client
INSERT INTO api_client_user_roles (id, api_client_id, role_id, status, created_by, created_at)
VALUES (gen_random_uuid(),
        'f8c2a812-5d2e-4e1d-8c8a-a1f91d97e1b7',
        'c8e22c9f-18c8-1239-b107-6a77dec9c354',
        1, '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP);

-- ---- V1_1_3__seed_sidebar_menus.sql ----
-- V1_1_3__seed_sidebar_menus.sql  (TENANT database)
-- Purpose: A tenant's sidebar on the day their database is provisioned.
--
-- Deliberately smaller than the administrator's: a tenant sees their own workspace, not the
-- screens that manage every tenant. Same ids as the system seed would be wrong — these are
-- different rows in a different database, and nothing joins them.
-- ============================================================================

INSERT INTO sidebar_menus (
    id, menu_type, parent_id, menu_code, title, description,
    path, icon, order_index, section_code, status, created_by, created_at
) VALUES
    ('c2e4f3d5-6b7c-4d8e-9f0a-1b2c3d4e5f01', 1, NULL, 'dashboard', 'Dashboard',
     'Landing page after sign-in', '/dashboard', 'dashboard', 1, 'main', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP),
    ('c2e4f3d5-6b7c-4d8e-9f0a-1b2c3d4e5f02', 1, NULL, 'examples', 'Examples',
     'Reference screens shipped with the template', '/examples', 'examples', 2, 'main', 1,
     '2d9ef613-8e52-4ef1-9079-5d4c3871e188', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- ---- V1_1_30__seed_example_entity.sql ----
-- V1_1_30__seed_example_entity.sql
-- Purpose: Seed per-tenant reference data into example_entity
-- QCP versioning: V1_1_x = insert/seed data (DML), patch 1 = example_entity table
--
-- ${tenant_code} is supplied by TenantProvisioningService when migrating each tenant
-- database — the seeded rows name their tenant, making DB-per-tenant isolation visible
-- when the same endpoint is called with different Host headers.

INSERT INTO example_entity (name, description) VALUES
('Sample Example A (tenant ${tenant_code})', 'Seeded row in the isolated ${tenant_code} database'),
('Sample Example B (tenant ${tenant_code})', 'Second seeded row for tenant ${tenant_code}');

-- ---- V1_1_31__remove_examples_sidebar_menu.sql ----
-- V1_1_31__remove_examples_sidebar_menu.sql  (TENANT database)
-- Purpose: The example feature (V1_0_30/V1_1_30) was replaced by the real DIY Upload Admin
-- feature — its "examples" sidebar entry (V1_1_3) now points at a route with no backend.
-- Migrations are forward-only, so this removes the row rather than editing V1_1_3 in place.

DELETE FROM sidebar_menus WHERE menu_code = 'examples';

-- ---- V1_2_0__create_config_locks.sql ----
-- V1_2_0__create_config_locks.sql
-- Purpose: Replace the single-holder processes.config_locked/config_lock_ref/config_locked_at
-- columns with a proper multi-holder lock table. S3UploadServiceImpl acquires one lock row per
-- upload, and a process can have several uploads in flight for it at once — the process must stay
-- locked against maker-admin config edits (TemplateServiceImpl/ProcessServiceImpl) for as long as
-- ANY of them holds a row, not just whichever one most recently acquired. A single ref column
-- can't represent that: an earlier upload finishing first would wrongly clear the lock while a
-- later one is still running. lock_ref is the uploadId (later reassigned to the Kafka batchId,
-- see ConfigLockService#reassignRef) — globally unique via IdGenerator/UUID, hence the PK here.

CREATE TABLE config_locks (
  process_id  TEXT NOT NULL REFERENCES processes (process_id) ON DELETE CASCADE,
  lock_ref    TEXT PRIMARY KEY,
  locked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT config_locks_ref_not_blank CHECK (btrim(lock_ref) <> '')
);

-- "Is this process locked" check (TemplateServiceImpl/ProcessServiceImpl edit guards)
CREATE INDEX config_locks_process_idx ON config_locks (process_id);
-- Stale-lock reaper scan (ConfigLockReaper)
CREATE INDEX config_locks_locked_at_idx ON config_locks (locked_at);

-- Carry forward any lock already in flight at deploy time so a mid-upload process doesn't lose
-- its lock across the cutover.
INSERT INTO config_locks (process_id, lock_ref, locked_at)
SELECT process_id, config_lock_ref, coalesce(config_locked_at, now())
FROM processes
WHERE config_locked = TRUE AND config_lock_ref IS NOT NULL;

ALTER TABLE processes
  DROP CONSTRAINT processes_lock_ref_when_locked,
  DROP COLUMN config_locked,
  DROP COLUMN config_lock_ref,
  DROP COLUMN config_locked_at;

COMMENT ON TABLE config_locks IS
  'One row per in-flight upload holding a process''s config lock (S3UploadServiceImpl). A process '
  'is locked against maker-admin edits whenever any row exists for it; releasing one upload''s row '
  'never affects another upload''s row for the same process.';

-- ==================================================================================
-- The sections below fold in every migration that previously ran as its own version
-- (V1_1_0 through V1_4_20) after this file's original V1_0_0..V1_2_0 baseline. See this
-- file's header for why two of the original DROP COLUMN statements are omitted.
-- ==================================================================================

-- ---- V1_1_0__seed_vendor_onboarding_demo.sql ----
-- Purpose: Seed a ready-to-use "Vendor Onboarding" process + template into every tenant database,
-- demonstrating the full template config schema end to end: 10 template_fields, 1
-- template_pk_fields row, 8 template_validation_rules (one of each non-reserved rule_type), and 4
-- template_transformations. process/template are seeded directly as 'active', bypassing the
-- maker-checker workflow. process_id/template_id are literal ('proc-000001'/'tmpl-000001') to line
-- up with what V1_3_2's sequences hand out as their first values.
--
-- Once provisioned, upload a file with headers matching the source_column values below against:
--   POST /api/v1/uploads/proc-000001/tmpl-000001

INSERT INTO processes
  (process_id, process_name, description, status, validations_enabled, created_by)
VALUES
  ('proc-000001', 'Vendor Onboarding (seed demo)',
   'Demo process seeded by V1_1_0 — bulk vendor master creation.',
   'active', TRUE, 'seed_script');

-- template_upload_formats is auto-seeded by the templates_seed_formats trigger.
INSERT INTO templates
  (template_id, template_code, template_name, template_description, process_id, status,
   duplicate_action, row_order,
   post_load_action_type, kafka_topic, kafka_bootstrap_servers,
   validations_enabled, maker_checker_enabled, created_by)
VALUES
  ('tmpl-000001', 'TPL_VENDOR_ONBOARDING_SEED', 'Vendor Master Upload (seed demo)',
   'Demo template seeded by V1_1_0.', 'proc-000001',
   'active',
   'overwrite', 'inputSequence',
   'kafka', 'vendor-onboarding-data-load', 'localhost:9092',
   TRUE, FALSE, 'seed_script');

INSERT INTO template_fields
  (template_id, source_column, target_field, field_label, field_type, required, sort_order)
VALUES
  ('tmpl-000001', 'Vendor Code',            'vendor_code',      'Vendor Code',            'string',  TRUE,  0),
  ('tmpl-000001', 'Vendor Name',             'vendor_name',      'Vendor Name',            'string',  TRUE,  1),
  ('tmpl-000001', 'Email Address',           'email',            'Email Address',          'string',  TRUE,  2),
  ('tmpl-000001', 'Phone Number',            'phone_number',     'Phone Number',           'string',  TRUE,  3),
  ('tmpl-000001', 'GST Number',              'gst_number',       'GST Number',             'string',  FALSE, 4),
  ('tmpl-000001', 'PAN Number',              'pan_number',       'PAN Number',             'string',  TRUE,  5),
  ('tmpl-000001', 'State',                   'state',            'State',                  'string',  FALSE, 6),
  ('tmpl-000001', 'Annual Turnover (INR)',   'annual_turnover',  'Annual Turnover (INR)',  'number',  FALSE, 7),
  ('tmpl-000001', 'Onboarding Date',         'onboarding_date',  'Onboarding Date',        'date',    FALSE, 8),
  ('tmpl-000001', 'GST Registered (Y/N)',    'gst_registered',   'GST Registered',         'boolean', FALSE, 9);

INSERT INTO template_pk_fields
  (template_id, target_field, sort_order)
VALUES
  ('tmpl-000001', 'vendor_code', 0);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, pattern, sort_order)
VALUES
  ('tmpl-000001', 'gst_number', 'FORMAT_REGEX', 'ERROR',
   'GST number must match the standard 15-character GSTIN format',
   '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$', 0);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, required, reject_empty_string, reject_whitespace, sort_order)
VALUES
  ('tmpl-000001', 'vendor_name', 'NULL_EMPTY', 'ERROR',
   'Vendor Name is mandatory and cannot be blank', TRUE, TRUE, TRUE, 1);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, allowed_values, case_insensitive, sort_order)
VALUES
  ('tmpl-000001', 'state', 'ENUM', 'ERROR',
   'State must be one of the approved states',
   ARRAY['Maharashtra','Karnataka','Delhi','Tamil Nadu','Gujarat'], TRUE, 2);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, decimal_places, delimiter, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'DECIMAL_PRECISION', 'WARNING',
   'Annual turnover should not carry more than 2 decimal places', 2, '.', 3);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, min_value, max_value, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'RANGE', 'ERROR',
   'Annual turnover must be between 0 and 10,000,000,000', 0, 10000000000, 4);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, format, sort_order)
VALUES
  ('tmpl-000001', 'onboarding_date', 'DATE_FORMAT', 'ERROR',
   'Onboarding Date must be in yyyy-MM-dd format', 'yyyy-MM-dd', 5);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, expression, formula_terms, formula_operators, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'FUNCTIONAL', 'ERROR',
   'Annual turnover must not be negative',
   'annual_turnover - 0 >= 0',
   '[{"kind":"field","field":"annual_turnover"},{"kind":"constant","value":0}]'::jsonb,
   '["subtract"]'::jsonb, 6);

INSERT INTO template_validation_rules
  (template_id, field, rule_type, severity, message, compare_operator, group_by_field, transaction_split, condition, sort_order)
VALUES
  ('tmpl-000001', 'annual_turnover', 'TRANSACTION', 'WARNING',
   'Grouped turnover for Maharashtra vendors should not exceed Karnataka vendors, among GST-registered rows',
   'lte', 'vendor_code',
   '{"splitField":"state","branchAValue":"Maharashtra","branchBValue":"Karnataka","amountField":"annual_turnover"}'::jsonb,
   '{"conditionField":"gst_registered","conditionOperator":"equals","conditionValue":"true"}'::jsonb, 7);

INSERT INTO template_transformations
  (template_id, target_field, mappings, sort_order)
VALUES
  ('tmpl-000001', 'state',
   '[{"from":"MH","to":"Maharashtra"},{"from":"KA","to":"Karnataka"},{"from":"DL","to":"Delhi"},{"from":"TN","to":"Tamil Nadu"},{"from":"GJ","to":"Gujarat"}]'::jsonb,
   0),
  ('tmpl-000001', 'gst_registered',
   '[{"from":"Y","to":"true"},{"from":"N","to":"false"},{"from":"Yes","to":"true"},{"from":"No","to":"false"},{"from":"1","to":"true"},{"from":"0","to":"false"}]'::jsonb,
   1),
  ('tmpl-000001', 'phone_number',
   '[{"from":"0","to":"+91"}]'::jsonb,
   2),
  ('tmpl-000001', 'gst_number',
   '[{"from":"N/A","to":""},{"from":"NA","to":""},{"from":"-","to":""}]'::jsonb,
   3);

-- ---- V1_1_1__seed_dev_bypass_maker_user.sql ----
-- Purpose: seed data for DevBypassAuthenticationFilter's default actorIds (maker_admin_01 /
-- checker_admin_01), granting both the vendor-onboarding demo process via a shared dev-bypass role.

INSERT INTO maker_users
  (user_id, username, full_name, is_active, status, created_by)
VALUES
  ('maker_admin_01', 'dev.bypass.maker', 'Dev Bypass Maker Actor (seed)', TRUE, 'active', 'seed_script'),
  ('checker_admin_01', 'dev.bypass.checker', 'Dev Bypass Checker Actor (seed)', TRUE, 'active', 'seed_script');

INSERT INTO upload_roles
  (role_id, role_name, description, is_active, status, created_by)
VALUES
  ('role-seed-dev-bypass', 'Dev Bypass Upload Access (seed)',
   'Grants the seeded dev-bypass maker/checker users access to the V1_1_0 vendor-onboarding demo process.',
   TRUE, 'active', 'seed_script');

INSERT INTO upload_role_processes (role_id, process_id)
VALUES
  ('role-seed-dev-bypass', 'proc-000001');

INSERT INTO maker_user_roles (user_id, role_id)
VALUES
  ('maker_admin_01', 'role-seed-dev-bypass'),
  ('checker_admin_01', 'role-seed-dev-bypass');

-- ---- V1_1_2__seed_keycloak_batch_upload_users.sql ----
-- Purpose: seed maker_users rows keyed by the qc realm's real Keycloak ids
-- (qc.maker-batch-upload / qc.checker-batch-upload), now that DevBypassAuthenticationFilter is
-- off. Reuses V1_1_1's role-seed-dev-bypass role.

INSERT INTO maker_users
  (user_id, username, full_name, is_active, status, created_by)
VALUES
  ('131d70a9-62aa-4b0c-9d5b-de9e5859a738', 'qc.maker-batch-upload', 'qc MakerBatchUpload (seed)', TRUE, 'active', 'seed_script'),
  ('c72f4a2e-a538-4571-8db3-ee32604b93d0', 'qc.checker-batch-upload', 'qc CheckerBatchUpload (seed)', TRUE, 'active', 'seed_script')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO maker_user_roles (user_id, role_id)
VALUES
  ('131d70a9-62aa-4b0c-9d5b-de9e5859a738', 'role-seed-dev-bypass'),
  ('c72f4a2e-a538-4571-8db3-ee32604b93d0', 'role-seed-dev-bypass')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ---- V1_2_0__add_row_status_to_batch_upload_result_rows.sql ----
-- Purpose: historical only — batch_upload_result_rows (and this column) are dropped later by
-- V1_4_6 in this same file. Kept here, in order, for fidelity with the original migration
-- sequence; net final state has no such table or column.

ALTER TABLE batch_upload_result_rows
    ADD COLUMN row_status VARCHAR(10) NOT NULL DEFAULT 'FAILED';

CREATE INDEX idx_batch_upload_result_rows_batch_status ON batch_upload_result_rows (batch_id, row_status);

-- ---- V1_2_1__add_result_export_to_batch_upload_results.sql ----
-- Purpose: once ValidatedResultS3Exporter writes the row-by-row CSV to S3, its bucket/key are
-- recorded here so the maker UI can offer a "download validated file" link. Nullable.

ALTER TABLE batch_upload_results
    ADD COLUMN result_s3_bucket TEXT,
    ADD COLUMN result_s3_key TEXT;

-- ---- V1_3_0__upload_operator_pipeline.sql ----
-- Purpose: upload-api-contract.md (v1.1.0) is now implemented — adds the three columns
-- upload_attempts was missing for the maker/checker flow (S3 object keys for the raw/validated
-- stages, and the Kafka batchId correlating an attempt to its validation-service run).

ALTER TABLE upload_attempts
  ADD COLUMN raw_object_key       TEXT,
  ADD COLUMN validated_object_key TEXT,
  ADD COLUMN batch_id             UUID;

CREATE UNIQUE INDEX upload_attempts_batch_id_uidx ON upload_attempts (batch_id) WHERE batch_id IS NOT NULL;

COMMENT ON TABLE upload_attempts IS
  'One row per file a maker uploads for validation (upload-api-contract.md §2). raw_object_key/validated_object_key track the interim-storage stages (§6); batch_id correlates to batch_upload_results once validation starts.';
COMMENT ON TABLE upload_submissions IS
  'A maker''s attempt handed to a checker for review when the template has maker-checker enabled (upload-api-contract.md §2.4, §4).';
COMMENT ON TABLE upload_jobs IS
  'Post-load-action delivery job, created either directly (maker-checker disabled) or after checker acceptance (upload-api-contract.md §2.4, §4.3).';

-- ---- V1_3_1__drop_upload_attempts_one_active_per_process.sql ----
-- Purpose: "one active (non-terminal) upload attempt per process" was never an intended product
-- requirement. Drops the DB-level backstop for that rule.

DROP INDEX upload_attempts_one_active_per_process_uidx;

-- ---- V1_3_2__sequential_process_template_ids.sql ----
-- Purpose: process_id/template_id now come from a dedicated DB sequence instead of random hex, so
-- ids sort in creation order (proc-000001, proc-000002, ...). Sequences are bumped past whatever
-- numeric suffix already exists (covers the seed rows from V1_1_0 above) so the next app-generated
-- id never collides with seeded data.

CREATE SEQUENCE process_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE template_id_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION generate_sequential_id(prefix TEXT, seq_name TEXT)
RETURNS TEXT
LANGUAGE sql
VOLATILE
AS $$
  SELECT prefix || '-' || lpad(nextval(seq_name::regclass)::text, 6, '0');
$$;

SELECT setval('process_id_seq',
  coalesce((SELECT max(substring(process_id from 'proc-([0-9]+)$')::bigint) FROM processes), 0),
  true);
SELECT setval('template_id_seq',
  coalesce((SELECT max(substring(template_id from 'tmpl-([0-9]+)$')::bigint) FROM templates), 0),
  true);

ALTER TABLE processes ALTER COLUMN process_id SET DEFAULT generate_sequential_id('proc', 'process_id_seq');
ALTER TABLE templates ALTER COLUMN template_id SET DEFAULT generate_sequential_id('tmpl', 'template_id_seq');

-- ---- V1_3_3__add_job_dispatch_audit_events.sql ----
-- Purpose: PostLoadActionDispatcherImpl records two pipeline events not in the original SD §12.3
-- catalogue; audit_events.event_code has an FK into audit_event_catalogue, so both codes must
-- exist here before AuditEventService can record them.

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  ('JOB_DISPATCH_PUSHED', 'PIPELINE', 'Job''s completed file streamed to its template''s configured Kafka topic', NULL),
  ('JOB_DISPATCH_FAILED', 'PIPELINE', 'Failed to stream a job''s completed file to Kafka',                       NULL);

-- ---- V1_4_0__create_queue_configs.sql ----
-- Purpose: Queue Orchestration — reusable Kafka producer + topic settings bound to a consumer
-- callback contract (an existing api_configs row), so a template's Kafka post-load action can bind
-- to a saved queue by topic name instead of typing kafka_topic/kafka_bootstrap_servers by hand.
-- Adds templates.kafka_mode/kafka_queue_config_id and extends v_checker_inbox with a queueConfig
-- branch.

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

ALTER TABLE templates
  ADD COLUMN IF NOT EXISTS kafka_mode VARCHAR(20)
    CHECK (kafka_mode IS NULL OR kafka_mode IN ('useExisting','custom')),
  ADD COLUMN IF NOT EXISTS kafka_queue_config_id TEXT
    REFERENCES queue_configs (queue_config_id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS templates_queue_config_idx ON templates (kafka_queue_config_id);

COMMENT ON COLUMN templates.kafka_mode IS
  'When post_load_action_type = kafka: useExisting binds kafka_queue_config_id (a saved Queue Orchestration config, which supplies the topic/producer/consumer-callback settings); custom (or NULL, for templates saved before Queue Orchestration existed) uses kafka_topic/kafka_bootstrap_servers directly.';

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

-- ---- V1_4_1__seed_queue_and_api_configs_demo.sql ----
-- Purpose: reference/demo rows for api_configs and Queue Orchestration, so the Admin > API
-- Configuration and Admin > Queue Orchestration screens aren't empty on a fresh tenant database.

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

-- ---- V1_4_2__drop_row_order_and_template_sort_fields.sql ----
-- Purpose: dataLoad.rowOrder / sortByKey was persisted config only — nothing ever read it back to
-- reorder rows during upload processing.

ALTER TABLE templates DROP COLUMN row_order;

DROP TABLE template_sort_fields;

-- ---- V1_4_3__drop_duplicate_action_and_template_pk_fields.sql ----
-- Purpose: dataLoad.duplicateAction (reject/skipSilent/overwrite) was persisted config only —
-- nothing ever read it back to dedup/upsert rows during upload processing.

ALTER TABLE templates DROP COLUMN duplicate_action;

DROP TABLE template_pk_fields;

-- ---- V1_4_5__drop_warning_count_columns.sql ----
-- Purpose: the WARNING/ERROR rule severity distinction is deprecated on the validation-service
-- side, so there is no longer a separate passed-with-warning count downstream either.

ALTER TABLE upload_jobs DROP COLUMN warning_records;

ALTER TABLE batch_upload_results DROP COLUMN warning_count;

-- ---- V1_4_6__drop_batch_upload_result_rows_table.sql ----
-- Purpose: batch_upload_result_rows was a local mirror of every row validation-service holds;
-- rows browsing is now on-demand straight against validation-service instead.

DROP TABLE batch_upload_result_rows;

-- ---- V1_4_7__add_pre_staged_processing_key_to_submissions.sql ----
-- Purpose: historical only — this column is renamed to source_object_key by V1_4_15 below.

ALTER TABLE upload_submissions ADD COLUMN pre_staged_processing_object_key TEXT;

-- ---- V1_4_9__document_transaction_split_columns_mode.sql ----
-- Purpose: transaction_split now also carries a "differentColumns" mode (branchAField/
-- branchBField summed directly, no category matching) alongside the original "sameColumn" shape —
-- comment-only update, no column/constraint change.

COMMENT ON COLUMN template_validation_rules.transaction_split IS
  'TRANSACTION rules only: {mode, splitField, branchAValue, branchBValue, amountField, branchAField, branchBField}. mode "sameColumn" (default/null): splits rows into two categories via splitField vs branchAValue/branchBValue, sums amountField per category. mode "differentColumns": branchAField/branchBField are already-separate amount columns, summed directly with no category matching. Either way, the two branch sums are compared using compare_operator.';

-- ---- V1_4_10__make_queue_configs_topic_name_nullable.sql ----
-- Purpose: Queue Config create only captures queueConfigName — producer/topic are filled in later
-- via Update, same "shell first" pattern the wizard already uses for apiConfigId.

ALTER TABLE queue_configs ALTER COLUMN topic_name DROP NOT NULL;

-- ---- V1_4_11__add_admin_queue_audit_events.sql ----
-- Purpose: QueueConfigServiceImpl records ADMIN_QUEUE_* events via AuditEventService; the codes
-- must exist in audit_event_catalogue before they can be recorded.

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  ('ADMIN_QUEUE_CREATED',   'ADMIN', 'Queue config created (draft)',                  'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_UPDATED',   'ADMIN', 'Queue config edited',                           'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_SUBMITTED', 'ADMIN', 'Queue config submitted for approval',           'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_ACTIVATED', 'ADMIN', 'Queue config approved and activated',           'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_REJECTED',  'ADMIN', 'Queue config rejected by Checker Admin',        'admin-api-contract.md §7');

-- ---- V1_4_12__strip_legacy_warning_records_from_summaries.sql ----
-- Purpose: data hygiene — strips a stale warningRecords key some pre-existing upload_attempts.
-- summary JSONB values may carry from before the warning tile/severity column was removed.

UPDATE upload_attempts
SET summary = summary - 'warningRecords'
WHERE summary ? 'warningRecords';

-- ---- V1_4_13__create_upload_job_callback_results.sql ----
-- Purpose: local summary of one consumer-callback-service delivery run for an upload_jobs row —
-- how many batches were sent to the job's configured outbound API and how many got a successful
-- response. Mirrors batch_upload_results' role for diy-validation-service.

CREATE TABLE IF NOT EXISTS upload_job_callback_results (
  job_id         TEXT PRIMARY KEY REFERENCES upload_jobs (job_id) ON DELETE CASCADE,
  status         TEXT NOT NULL,
  total_batches  INTEGER NOT NULL DEFAULT 0 CHECK (total_batches >= 0),
  success_count  INTEGER NOT NULL DEFAULT 0 CHECK (success_count >= 0),
  failed_count   INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
  received_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE upload_job_callback_results IS
  'One row per job once consumer-callback-service reports it has attempted every batch — the reverse leg of PostLoadActionDispatcherImpl''s Kafka publish. Batch-level detail (per-chunk HTTP status/error) is not duplicated here; fetch it from consumer-callback-service on demand if a drill-down view is ever needed.';

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  ('JOB_CALLBACK_COMPLETED', 'PIPELINE', 'consumer-callback-service finished attempting delivery of a job''s batches to its configured outbound API', NULL);

-- ---- V1_4_14__create_queue_config_outbox.sql ----
-- Purpose: transactional outbox for publishing QueueConfig/ApiConfig changes to
-- consumer-callback-service over Kafka's queue-config-topic, instead of a synchronous REST push at
-- save time. A row here is written in the same DB transaction as the queue_configs/api_configs
-- change it describes.

CREATE TABLE IF NOT EXISTS queue_config_outbox (
  outbox_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  queue_config_id TEXT NOT NULL,
  event_key       TEXT NOT NULL,
  payload         JSONB NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS queue_config_outbox_created_at_idx ON queue_config_outbox (created_at);

COMMENT ON TABLE queue_config_outbox IS
  'Pending queue-config-topic events, oldest first. event_key is {tenantCode}:{queueConfigId} — the Kafka message key, so consumer-callback-service can compact on it and rebuild a per-queue-config cache. Emptied continuously by QueueConfigOutboxPublisher, not read anywhere else.';

-- ---- V1_4_15__replace_pre_staged_processing_with_source_object_key.sql ----
-- Purpose: removes the pending_processing S3 stage. source_object_key names the real validated/
-- raw object directly instead of a throwaway copy of it; backfills from
-- pre_staged_processing_object_key for any in-flight row.

ALTER TABLE upload_submissions
  ADD COLUMN IF NOT EXISTS source_object_key TEXT;

UPDATE upload_submissions
SET source_object_key = pre_staged_processing_object_key
WHERE source_object_key IS NULL AND pre_staged_processing_object_key IS NOT NULL;

ALTER TABLE upload_submissions
  DROP COLUMN IF EXISTS pre_staged_processing_object_key;

COMMENT ON COLUMN upload_submissions.source_object_key IS
  'The validated (or raw, if validation was skipped) object this submission was made from — CheckerServiceImpl#accept reads this straight through as the job''s completedFileKey. Replaces pre_staged_processing_object_key; there is no longer a separate pending_processing copy to pre-stage.';

-- ---- V1_4_16__add_result_export_to_upload_job_callback_results.sql ----
-- Purpose: once ProcessedResultS3Exporter writes the per-batch JSON export to S3, its bucket/key
-- are recorded here so the maker UI can offer a "download processed results" link.

ALTER TABLE upload_job_callback_results
    ADD COLUMN result_s3_bucket TEXT,
    ADD COLUMN result_s3_key TEXT;

-- ---- V1_4_17__seed_keycloak_viewer_batch_upload_user.sql ----
-- Purpose: seed maker_users row for the viewerBatchUpload realm role
-- (qc.viewer-batch-upload), reusing V1_1_1's role-seed-dev-bypass role.

INSERT INTO maker_users
  (user_id, username, full_name, is_active, status, created_by)
VALUES
  ('4c036236-2908-4166-9a1b-35215c1695e4', 'qc.viewer-batch-upload', 'qc ViewerBatchUpload (seed)', TRUE, 'active', 'seed_script')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO maker_user_roles (user_id, role_id)
VALUES
  ('4c036236-2908-4166-9a1b-35215c1695e4', 'role-seed-dev-bypass')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ---- V1_4_18__add_admin_pipeline_override_audit_events.sql ----
-- Purpose: ViewerServiceImpl's admin-only retry/reject-fail overrides record
-- ADMIN_ATTEMPT_*/ADMIN_SUBMISSION_*/ADMIN_JOB_* events; the codes must exist in
-- audit_event_catalogue before they can be recorded.

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  ('ADMIN_ATTEMPT_RETRIED',    'ADMIN', 'Upload attempt manually reset to ACCEPTED by an admin',        'viewer-dashboard'),
  ('ADMIN_ATTEMPT_REJECTED',   'ADMIN', 'Upload attempt manually aborted by an admin',                   'viewer-dashboard'),
  ('ADMIN_SUBMISSION_RETRIED', 'ADMIN', 'Checker submission manually reset to WAITING_FOR_CHECKER by an admin', 'viewer-dashboard'),
  ('ADMIN_SUBMISSION_EXPIRED', 'ADMIN', 'Checker submission manually expired by an admin',               'viewer-dashboard'),
  ('ADMIN_JOB_RETRIED',        'ADMIN', 'Processing job manually reset to QUEUED by an admin',           'viewer-dashboard'),
  ('ADMIN_JOB_REJECTED',       'ADMIN', 'Processing job manually marked FAILED by an admin',              'viewer-dashboard');

-- ---- V1_4_19__add_consumer_concurrency_to_queue_configs.sql ----
-- Purpose: lets an admin set how many concurrent Kafka consumer threads consumer-callback-service
-- runs for a queue config's topic. Defaults to 1 to preserve prior behavior for every existing row.

ALTER TABLE queue_configs
  ADD COLUMN topic_consumer_concurrency INTEGER NOT NULL DEFAULT 1
    CHECK (topic_consumer_concurrency BETWEEN 1 AND 1000);

-- ---- V1_4_20__drop_topic_bootstrap_servers_from_queue_configs.sql ----
-- Purpose: queue_configs.topic_bootstrap_servers is removed — consumer-callback-service always
-- connects with its own shared spring.kafka.bootstrap-servers and never read the field.

ALTER TABLE queue_configs DROP COLUMN IF EXISTS topic_bootstrap_servers;
