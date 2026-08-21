-- V1_0_1__create_images.sql
-- Purpose: Binary asset store for all uploaded files in the system (admin) database.

-- =============================================
-- image schema tables
-- =============================================

-- Image storage for uploaded files (profile pictures, documents, etc.)
CREATE TABLE IF NOT EXISTS image.images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name TEXT NOT NULL,                          -- Original filename as uploaded
    file_type VARCHAR(50) NOT NULL,                   -- MIME type (e.g., image/png, image/jpeg)
    file_size BIGINT NOT NULL,                        -- Size in bytes
    file_data BYTEA NOT NULL,                         -- Binary content of the image
    description TEXT NOT NULL,                        -- User-provided or system-generated caption
    uploaded_by_type INTEGER NOT NULL,                -- Discriminator for uploader entity type
    uploaded_by UUID NOT NULL,                        -- Reference to uploading entity (FK added later)
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reference_id UUID                                 -- Optional link to owning entity
);

-- Speed up lookups by uploader
CREATE INDEX IF NOT EXISTS idx_images_uploaded_by ON image.images (uploaded_by);

-- Speed up lookups by reference entity
CREATE INDEX IF NOT EXISTS idx_images_reference_id ON image.images (reference_id);

-- Filter by file type
CREATE INDEX IF NOT EXISTS idx_images_file_type ON image.images (file_type);
