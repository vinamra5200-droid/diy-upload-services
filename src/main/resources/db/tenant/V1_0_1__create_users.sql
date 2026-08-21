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
