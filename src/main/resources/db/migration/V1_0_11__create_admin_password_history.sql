-- V1_0_11__create_admin_password_history.sql
-- Purpose: Password reuse-prevention history for admin users.

-- =============================================
-- Admin password history table
-- =============================================

-- Tracks password history for users to prevent password reuse
CREATE TABLE IF NOT EXISTS auth.admin_password_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    password VARCHAR(255) NOT NULL,            -- Hashed password value
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Speed up lookups by user
CREATE INDEX IF NOT EXISTS idx_admin_password_history_user_id ON auth.admin_password_history (user_id);

-- Speed up lookups by creation time for password age checks
CREATE INDEX IF NOT EXISTS idx_admin_password_history_created_at ON auth.admin_password_history (created_at);

-- =============================================
-- Foreign key constraints for auth.admin_password_history
-- =============================================

ALTER TABLE auth.admin_password_history
    ADD CONSTRAINT fk_admin_password_history_user_id FOREIGN KEY (user_id)
        REFERENCES auth.users (id)
        ON DELETE CASCADE;
