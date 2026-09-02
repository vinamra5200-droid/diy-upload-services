# Implementation Plan: diy-upload-web (callback delivery visibility)

Companion to `consumer-callback-service-plan.md`. Specifies the UI work needed once
consumer-callback-service reports batch-delivery outcomes back to diy-upload-services. All APIs
referenced here already exist in diy-upload-services — see `controller.UploadSubmissionJobController`
and `dto.response.UploadJobCallbackSummaryResponse` in that repo.

## 1. Scope

Surface, on a maker's job detail view, whether the job's batches were actually delivered to the
configured outbound API — not just that the job was dispatched to Kafka.

## 2. API contract

```
GET /api/v1/upload/jobs/{jobId}/callback-summary
Authorization: Bearer <token>   (role: makerBatchUpload)
```

**200 OK** — job's callback has completed:

```json
{
  "status": "success",
  "statusCode": 200,
  "data": {
    "jobId": "string",
    "status": "string",
    "totalBatches": 0,
    "successCount": 0,
    "failedCount": 0,
    "receivedAt": "2026-08-31T10:00:00Z"
  }
}
```

**404** — no completion callback received yet (job is still `PROCESSING`, or hasn't been dispatched
at all). This is an expected, normal state, not an error condition — render it as "delivery in
progress" / "not yet dispatched," not as a failure banner.

**403** — job exists but doesn't belong to the current actor (same ownership check as
`download`/`dispatch`).

## 3. UI changes

### 3.1 Job detail — callback delivery section

New section on the job detail view:

- Call `callback-summary` for the open job.
- On 200: show `successCount / totalBatches` prominently, `failedCount` called out distinctly (not
  buried) if non-zero, and `receivedAt`.
- On 404: show a "delivery in progress" state instead of an error. Do not show this section at all
  for a job whose `status` is not yet `PROCESSING`/`COMPLETED`/`FAILED` (i.e. still `QUEUED` —
  dispatch hasn't happened).
- `failedCount > 0` needs its own visual treatment distinct from a clean run — `JobStatus` alone
  collapses "all batches delivered" and "some batches permanently failed" into the same `FAILED`
  value (see diy-upload-services' `UploadJobCallbackResultServiceImpl` — any `failedCount > 0`
  currently maps the job to `FAILED`, since there's no partial-success `JobStatus` today). This
  summary response is the only place the nuance survives, so the UI is where a maker actually sees
  "12 of 15 batches delivered" instead of just "Failed."

### 3.2 Keeping the status current

There is no push/SSE channel for job-callback completion today (unlike upload-attempt completion,
which does have one). Two options:

- **MVP**: poll `callback-summary` on an interval while the job's `status === 'PROCESSING'`; stop
  polling once it returns `COMPLETED` or `FAILED`. Simple, no backend changes needed.
- **Later**: add a push event, mirroring `UploadAttemptEventPublisher`'s SSE "done" event — would
  need a small addition to `UploadJobCallbackResultServiceImpl.recordCompletion` on the backend to
  publish it. Bigger lift; not required for a first cut.

Recommend shipping with polling and revisiting SSE only if the polling interval proves too coarse
in practice.

### 3.3 Job status badges

Confirm `JobStatus.FAILED` renders distinctly depending on cause once this ships — it now means two
different things that look identical in the raw enum value:

1. Dispatch itself failed (`PostLoadActionDispatcherImpl` never got the file to Kafka) — no
   `callback-summary` will ever exist for this job.
2. Dispatch succeeded, but the callback-delivery step reported one or more permanent batch
   failures — `callback-summary` exists and explains the split.

A generic "Failed" badge with no drill-down loses this distinction; link the badge to the callback
delivery section (§3.1) when a summary exists.

## 4. Admin: ApiConfig form — body templating hint

Once the `{{payload}}` convention (see `consumer-callback-service-plan.md` §5/§8-2) is formally
adopted, add a hint or placeholder text to the `body` field on the ApiConfig create/edit screen
(the "Consumer Callback" step of Queue Orchestration) — e.g. "Include `{{payload}}` where the
batch's row data should be inserted." Without this, there is no signal in the UI that anything
needs to go in that field for batch data to actually reach the outbound call, and an admin has no
way to discover the convention other than reading backend code.

## 5. Out of scope for this pass

- Per-batch drill-down (which specific chunk failed and why) — that detail intentionally stays in
  consumer-callback-service's own database, not copied into diy-upload-services. If a drill-down
  view is wanted later, it needs a new on-demand proxy endpoint on diy-upload-services calling into
  consumer-callback-service's internal API (mirroring `ValidationServiceResultsClientImpl`), which
  is separate follow-up work, not assumed here.
- Queue Orchestration screen itself — no changes needed beyond §4; the screen already exists.
