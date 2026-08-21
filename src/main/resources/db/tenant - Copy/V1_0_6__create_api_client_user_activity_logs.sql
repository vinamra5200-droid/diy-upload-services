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
