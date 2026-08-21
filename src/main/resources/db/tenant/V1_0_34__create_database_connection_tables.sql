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
