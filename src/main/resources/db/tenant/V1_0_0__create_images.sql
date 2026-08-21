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
