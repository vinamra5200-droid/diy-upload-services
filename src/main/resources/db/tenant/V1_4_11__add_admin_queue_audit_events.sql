-- V1_4_11__add_admin_queue_audit_events.sql
-- Purpose: QueueConfigServiceImpl (V1_4_0) records ADMIN_QUEUE_* events via AuditEventService,
-- but those codes were never added to audit_event_catalogue. audit_events.event_code has an FK
-- into audit_event_catalogue, so every ADMIN_QUEUE_* call (create/update/submit/accept/reject)
-- fails with a foreign key violation until the codes exist here — same bug V1_3_3 fixed for
-- JOB_DISPATCH_*.

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  ('ADMIN_QUEUE_CREATED',   'ADMIN', 'Queue config created (draft)',                  'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_UPDATED',   'ADMIN', 'Queue config edited',                           'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_SUBMITTED', 'ADMIN', 'Queue config submitted for approval',           'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_ACTIVATED', 'ADMIN', 'Queue config approved and activated',           'admin-api-contract.md §7'),
  ('ADMIN_QUEUE_REJECTED',  'ADMIN', 'Queue config rejected by Checker Admin',        'admin-api-contract.md §7');
