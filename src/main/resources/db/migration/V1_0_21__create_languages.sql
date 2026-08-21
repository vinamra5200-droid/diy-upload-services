-- V1_0_21__create_languages.sql
-- Purpose: Global language catalog; tenant-level language enablement lives in per-tenant DBs.

-- =============================================
-- tenant schema tables (continued)
-- =============================================

-- Language configuration for multi-language support
CREATE TABLE IF NOT EXISTS tenant.languages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,              -- Language name in English
    unicode_name VARCHAR(100) NOT NULL UNIQUE,     -- Language name in native script
    description TEXT NOT NULL,
    short_code VARCHAR(10) UNIQUE NOT NULL,        -- ISO language code (e.g., 'en', 'hi')
    is_default INTEGER NOT NULL,                   -- 1=default language, 0=non-default
    status INTEGER NOT NULL,                       -- 1=active, 0=inactive
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Speed up lookups by short_code (frequently used for language selection)
CREATE INDEX IF NOT EXISTS idx_languages_short_code ON tenant.languages (short_code);

-- Speed up filtering by status
CREATE INDEX IF NOT EXISTS idx_languages_status ON tenant.languages (status);

-- Speed up filtering by default language
CREATE INDEX IF NOT EXISTS idx_languages_is_default ON tenant.languages (is_default);

-- =============================================
-- Foreign key constraints for tenant.languages
-- =============================================

ALTER TABLE tenant.languages
    ADD CONSTRAINT fk_languages_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

ALTER TABLE tenant.languages
    ADD CONSTRAINT fk_languages_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;
