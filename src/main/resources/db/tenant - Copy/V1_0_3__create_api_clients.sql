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
