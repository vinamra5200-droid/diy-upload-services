-- V1_3_3__add_job_dispatch_audit_events.sql
-- Purpose: PostLoadActionDispatcherImpl (upload_jobs -> Template.kafkaTopic, triggered by
-- POST /api/v1/upload/jobs/{jobId}/dispatch) records two new pipeline events not in the original
-- SD §12.3 catalogue. audit_events.event_code has an FK into audit_event_catalogue, so both codes
-- must exist here before AuditEventService can record them.

INSERT INTO audit_event_catalogue (event_code, category, description, sd_reference) VALUES
  ('JOB_DISPATCH_PUSHED', 'PIPELINE', 'Job''s completed file streamed to its template''s configured Kafka topic', NULL),
  ('JOB_DISPATCH_FAILED', 'PIPELINE', 'Failed to stream a job''s completed file to Kafka',                       NULL);
