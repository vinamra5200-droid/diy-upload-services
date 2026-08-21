-- V1_0_3__create_users.sql
-- Purpose: Admin user registry with self-referential audit columns and cross-FK to
--          image.images (profile picture) and auth.roles (back-referenced from V1_0_3).

-- =============================================
-- auth schema tables (continued)
-- =============================================

-- User registry containing authentication principals
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,                         -- Login handle shown in UI
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email_id VARCHAR(255) NOT NULL UNIQUE,                        -- Primary contact email
    mobile_number VARCHAR(10) NOT NULL UNIQUE,
    send_activation_email INTEGER NOT NULL,
    send_activation_sms INTEGER NOT NULL,
    password VARCHAR(500),                                         -- BCrypt hash
    password_creation_date TIMESTAMP WITH TIME ZONE,
    password_expiry_days INTEGER,
    password_invalid_attempts INTEGER,
    password_activation_key UUID,
    password_activation_key_valid_to TIMESTAMP WITH TIME ZONE,
    password_reset_key UUID,
    password_reset_key_valid_to TIMESTAMP WITH TIME ZONE,
    profile_image_id UUID,                                         -- FK to image.images added below
    status INTEGER NOT NULL,                                       -- Lifecycle state (active, locked, etc.)
    created_by UUID,                                               -- Self-referential FK added below
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,                                               -- Self-referential FK added below
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Support filtering by lifecycle state
CREATE INDEX IF NOT EXISTS idx_users_status ON auth.users (status);

-- Accelerate expiration checks
CREATE INDEX IF NOT EXISTS idx_users_password_activation_key_valid_to ON auth.users (password_activation_key_valid_to);
CREATE INDEX IF NOT EXISTS idx_users_password_reset_key_valid_to ON auth.users (password_reset_key_valid_to);

-- =============================================
-- Foreign key constraints for auth.users
-- =============================================

-- Self-referential FKs for audit columns
ALTER TABLE auth.users
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

ALTER TABLE auth.users
    ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

-- Link profile image to image.images
ALTER TABLE auth.users
    ADD CONSTRAINT fk_users_profile_image FOREIGN KEY (profile_image_id)
        REFERENCES image.images (id)
        ON DELETE SET NULL;

-- =============================================
-- Foreign key constraints for auth.roles
-- (auth.roles was created before auth.users — FKs deferred here)
-- =============================================

ALTER TABLE auth.roles
    ADD CONSTRAINT fk_roles_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

ALTER TABLE auth.roles
    ADD CONSTRAINT fk_roles_updated_by FOREIGN KEY (updated_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;

-- =============================================
-- Foreign key constraints for image.images
-- =============================================

-- Link uploaded_by to auth.users (assuming uploader is always a user)
ALTER TABLE image.images
    ADD CONSTRAINT fk_images_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES auth.users (id)
        ON DELETE SET NULL;
