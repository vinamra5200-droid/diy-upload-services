# Implementation Plan: consumer-callback-service

Companion to [`kafka_runtime_config_design.md`](kafka_runtime_config_design.md) and the Queue
Orchestration feature in this repo (`QueueConfig`/`ApiConfig`, `docs/standards/multi-tenancy.md`).
This document specifies what consumer-callback-service (a separate repository) needs to build. It
does not cover diy-upload-services changes already shipped there — see
`controller.UploadJobCallbackController` and `entity.UploadJobCallbackResult` for the receiving end
of the completion-callback contract this document produces against, and
`service.impl.QueueConfigEventPublisherImpl` / `scheduler.QueueConfigOutboxPublisher` for the
control-plane event feed this document consumes.

## 1. Scope

Two responsibilities:

1. **Consume** batches of row data that diy-upload-services publishes to Kafka once a job's
   post-load action dispatches (`PostLoadActionDispatcherImpl`).
2. **Call** the outbound REST API configured against each batch's queue (`api_configs`, maker-
   configured via the Queue Orchestration screen), and record + report how many batches succeeded.

The target of the outbound call is a third-party system unrelated to this platform — a plain HTTP
200/non-200 response is the entire success signal; no response body parsing is required.

## 2. Architecture overview

```mermaid
graph LR
    subgraph diy-upload-services
        PLA[PostLoadActionDispatcherImpl] -->|Kafka chunk messages| TOPIC[(admin-created topic,\nnamed by QueueConfig.topicName)]
        OUTBOX[(queue_config_outbox,\nper-tenant DB)] --> QCP[QueueConfigOutboxPublisher\n(scheduled poller)]
        QCP -->|queue-config-topic| QCT[(queue-config-topic,\ncompacted)]
    end

    subgraph consumer-callback-service
        TOPIC --> CONSUMER[Dynamic Kafka listeners]
        QCT --> CACHE[(local cache:\ntenant+topic -> ApiConfig)]
        CONSUMER --> CACHE
        CONSUMER --> HTTP[RestTemplate call\nper chunk]
        HTTP --> EXT[Third-party system]
        CONSUMER --> DB[(callback_batch_attempts,\nits own DB)]
        DB --> COMPLETE[Completion callback\nonce lastChunk attempted]
    end

    COMPLETE -->|POST /api/v1/upload-jobs/jobId/callback-completed| diy-upload-services
```

## 3. Control plane — resolving `(tenant, topic) → ApiConfig`

This service must not call diy-upload-services synchronously per message — that reintroduces the
duplication/coupling problem this design avoids. It needs a local, kept-current cache instead, and
that piece is now built on the diy-upload-services side: consume `queue-config-topic` (compacted,
keyed `{tenantCode}:{queueConfigId}`) to build it. Rebuild the cache from the beginning of the topic
on startup; apply updates as they arrive.

- Published by `QueueConfigOutboxPublisher` (a scheduled poller, default every 5s per tenant) off a
  transactional outbox (`queue_config_outbox`) written in the same DB transaction as the
  `queue_configs`/`api_configs` change that triggered it (`QueueConfigEventPublisherImpl`) — so an
  event is never lost between "saved to Postgres" and "sent to Kafka".
- **Only `active` queue configs are ever published.** A draft/waitingForChecker/rejected row never
  appears on the topic — nothing to act on. An edit to an already-`active` queue config (allowed
  directly, no re-review — see `ConfigLifecycleGuard.assertEditable`) and an edit to the `ApiConfig`
  an active queue config is bound to (`ApiConfigServiceImpl.update` fans out to every active queue
  config referencing it) both re-publish.
- Event value shape (`in.qualtechedge.qcp.templates.dto.request.QueueConfigEvent` in
  diy-upload-services):

```json
{
  "queueConfigId": "string",
  "tenantCode": "string",
  "topicName": "string",
  "status": "active",
  "updatedAt": "2026-08-31T10:00:00Z",
  "apiConfig": {
    "apiConfigId": "string",
    "method": "GET",
    "uri": "string",
    "queryParams": "[...]",
    "headers": "[...]",
    "body": "string"
  }
}
```

  `apiConfig` is `null` if the queue config has no bound API config yet (Topic step done, Consumer
  Callback step not). **`auth` is never included** — see §5's auth options below.
- There is currently no way to take an `active` queue config out of service (no deactivate/archive
  transition), so no tombstone events are published yet. If that gets added later, treat a `null`
  Kafka value on this topic as the tombstone convention.

**Cache key**: `(tenant_code, topic_name)`, never `topic_name` alone — `queue_configs.topic_name` is
only unique within one tenant's database, so two tenants can legitimately choose the same topic
name.

**Topic naming/ownership note**: `queue-config-topic` name and partition/replication settings are
diy-upload-services' to own (`qcp.kafka.topics.queue-config` in its `application.yaml`, default
`queue-config-topic`) — this service's own config must reference the same literal topic name, not
redefine it independently.

## 4. Data plane — dynamic Kafka subscription

Topic names are admin-chosen free text, created at runtime (`QueueConfigServiceImpl.accept`) — a
static `@KafkaListener(topics = "...")` cannot work here.

- Use `KafkaListenerEndpointRegistry` to register and start a listener container per
  `(tenant, topic)` the control-plane cache marks active; stop and unregister it when that entry is
  removed/tombstoned.
- Message shape (must deserialize exactly, field-for-field mirror of diy-upload-services'
  `PostLoadActionChunkMessage`):

```json
{
  "jobId": "string",
  "tenantCode": "string",
  "processCode": "string",
  "templateCode": "string",
  "templateVersion": "string",
  "chunkSequence": 0,
  "lastChunk": false,
  "totalRecords": 0,
  "rows": [ { "rowNumber": 0, "data": { "...": "..." } } ]
}
```

- `jobId` is the Kafka message key — one job's chunks land on one partition, in order.
- At-least-once delivery means a chunk can be redelivered (rebalance, retry). Track
  `(job_id, chunk_sequence)` as processed before acting on it, so a redelivery doesn't trigger a
  duplicate outbound call.

### 4a. Dead-letter topic — poison messages only

diy-upload-services provisions `<topicName>.DLT` on the broker alongside every queue config's
primary topic (`QueueConfigServiceImpl.accept`, same tolerant "already exists is fine" handling as
the primary topic) — deterministic naming, no new persisted field; consumer-callback-service
computes the identical name from its own copy of the same convention
(`in.qualtechedge.consumercallback.utils.DltTopics`, mirroring diy-upload-services'
`in.qualtechedge.qcp.templates.utils.DltTopics`). Fixed platform-wide config: `cleanup.policy=delete`
regardless of the primary topic's own policy, 30-day retention independent of the primary topic's
own (possibly much shorter) retention.

**Only a message `ChunkMessageHandlerImpl` cannot even deserialize** is dead-lettered — the raw JSON
is published as-is to `<topicName>.DLT`, keyed by `tenantCode`, with `x-dlt-*` headers (original
topic, tenant code, error, failed-at timestamp) for triage. Outbound HTTP delivery failures are
unaffected — see decision #4 above; they stay in `callback_batch_attempts` only, no Kafka involved.

Queue configs already `active` before this shipped won't have a `.DLT` topic until they're
re-`accept()`-ed — topic creation only happens in `accept()`, not `update()`. Not backfilled
automatically; same class of gap as the topic-name-collision risk already noted at the bottom of
this document.

## 5. Outbound delivery

- **One RestTemplate call per consumed chunk** — not per row, not per whole job. This preserves the
  producer's batching (chunks capped at `kafkaBatchProperties.postLoadActionChunkSize`, default 500
  rows) instead of multiplying it into up to 500x the HTTP calls.
- Resolve `method` / `uri` / `queryParams` / `headers` / `body` from the cached `ApiConfig` snapshot
  for the chunk's `(tenantCode, topicName)`.
- **Body templating convention (needs to be formally adopted and documented on the `ApiConfig`
  contract, not just implied here)**: `body` may contain a `{{payload}}` placeholder, substituted
  with the chunk's serialized `rows` (plus `jobId`/`chunkSequence`/`lastChunk` so the receiving
  system can reassemble). Confirm this with whoever owns the `ApiConfig` UI/docs before building
  the substitution logic.
- **Auth — do not persist `ApiConfig.auth` in this service's own database.** Two options, pick one
  before building the HTTP client:
  1. **Target**: read the secret directly from Vault at call time (Pattern B, per
     `docs/standards/vault-and-secrets-setup.md` in the diy-upload-services repo), with this
     service's own AppRole scoped read-only to that one subtree.
  2. **Interim**: omit `auth` from the cached snapshot (it already is, per §3); call a narrow,
     service-authenticated `resolve-auth` endpoint on diy-upload-services immediately before the
     outbound call, and never log or persist the response.
- Client config: mirror diy-upload-services' `RestTemplateConfig` (ssl-verify toggle for local/dev),
  plus explicit connect/read timeouts — this client calls arbitrary maker-configured URLs, unlike
  diy-upload-services' fixed-endpoint internal clients, so it needs its own timeout discipline.
- Retry with backoff on failure; after retries are exhausted, mark the batch permanently `FAILED`
  (dead-letter topic optional, or just a capped `attempt_count` on the tracking row — pick based on
  how much replay tooling you want later).

## 6. Traceability — local data model

Own database (see open decision on tenancy shape, §8).

```sql
CREATE TABLE callback_batch_attempts (
  job_id           TEXT NOT NULL,
  chunk_sequence   INTEGER NOT NULL,
  tenant_code      TEXT NOT NULL,
  api_config_id    TEXT NOT NULL,
  outcome          TEXT NOT NULL,      -- SUCCESS | FAILED
  http_status_code INTEGER,
  attempt_count    INTEGER NOT NULL DEFAULT 1,
  error_message    TEXT,
  attempted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (job_id, chunk_sequence)
);
```

The primary key doubles as the idempotency guard from §4 (`ON CONFLICT (job_id, chunk_sequence) DO
UPDATE` on retry, or a separate processed-set — either works).

## 7. Completion callback — contract is fixed, build directly against it

Once every chunk of a job (through the one where `lastChunk = true`) has been attempted:

```
POST {diy-upload-services-base-url}/api/v1/upload-jobs/{jobId}/callback-completed
X-Tenant-Code: {tenantCode}
Content-Type: application/json

{
  "jobId": "string",
  "tenantCode": "string",
  "status": "string",
  "totalBatches": 0,
  "successCount": 0,
  "failedCount": 0,
  "failedBatches": [
    { "chunkSequence": 0, "rowCount": 0, "httpStatusCode": 0, "errorMessage": "string" }
  ]
}
```

- This endpoint is **already idempotent** on the receiving side (claim/unclaim against
  `upload_job_callback_results`) — this service's own retry of the completion POST can be naive, no
  dedup logic needed here.
- It requires no Keycloak bearer token — it's excluded from auth and from Host-based tenant
  resolution on diy-upload-services' side, reachable only on the private network (same trust
  boundary as diy-validation-service's existing `validation-completed` callback).
- `failedBatches` should be built from the `FAILED` rows in `callback_batch_attempts` for this
  `job_id` — cap the list at a sane size (mirror diy-upload-services' own `MAX_ISSUES_PER_ATTEMPT`
  pattern, e.g. 1000) if a job could plausibly have more failed chunks than that.

## 8. Open decisions — status

| # | Decision | Resolution |
|---|---|---|
| 1 | Auth resolution strategy (§5) | **Deferred, not implemented.** `OutboundCallbackClientImpl` calls with method/uri/headers/queryParams/body only — a queue whose API needs auth cannot be called with credentials yet. Vault direct read is still the target. |
| 2 | `{{payload}}` body templating (§5) | **Implemented as proposed** in `OutboundCallbackClientImpl` — still needs the documentation update on the `ApiConfig` contract/UI hint (see `diy-upload-web-plan.md` §4). |
| 3 | Single system DB vs database-per-tenant for this service | **Database-per-tenant**, not the single-DB recommendation this doc originally made — the actual project skeleton already had the full multi-tenant scaffold provisioned when implementation started. `callback_batch_attempts` is a per-tenant table (`V1_2_0`). |
| 4 | Dead-letter topic vs capped `attempt_count` for exhausted retries (§5) | **Capped `attempt_count`**, no DLT for outbound delivery failures — `ON CONFLICT ... DO UPDATE` on `(job_id, chunk_sequence)` bumps the count on redelivery; a failed outbound call is recorded and reported via the completion callback (§7), not routed to Kafka. A DLT topic *is* provisioned per queue config now (`<topicName>.DLT`, `QueueConfigServiceImpl.accept`), but it only ever receives messages consumer-callback-service's `ChunkMessageHandlerImpl` can't even deserialize — see the new §4a below. |

## 9. Build order / dependencies — status

1. ~~diy-upload-services: `queue-config-topic` outbox + publisher (§3)~~ — done
   (`QueueConfigEventPublisherImpl`, `QueueConfigOutboxPublisher`, `V1_4_14`).
2. ~~consumer-callback-service: §4–§6 (Kafka consumption, outbound delivery, local tracking)~~ — done:
   `QueueConfigTopicConsumer` (control-plane cache, replays `queue-config-topic` from the beginning
   on every startup), `ChunkConsumerRegistryImpl` (dynamic per-`(tenant,topic)` listener containers),
   `OutboundCallbackClientImpl`, `callback_batch_attempts` (`V1_2_0`).
3. ~~consumer-callback-service: §7~~ — done: `CallbackCompletionClientImpl`, naive 3-attempt retry,
   wired against the already-shipped `callback-completed` endpoint. End-to-end path is code-complete
   but untested against a live broker/DB — no Kafka or Postgres available in the build sandbox.
4. diy-upload-web: see `diy-upload-web-plan.md` — not started.
5. ~~Both repos: DLT topic for poison messages (§4a)~~ — done: `QueueConfigServiceImpl.accept`
   provisions `<topicName>.DLT`; consumer-callback-service's `ChunkMessageHandlerImpl` publishes
   unparseable messages there via the new `DeadLetterPublisher`.

### Known residual risk found during implementation

`queue_configs.topic_name` uniqueness is only enforced per-tenant database (§3's caution). If two
tenants ever activate a queue config with the same topic name on the same shared Kafka cluster, both
tenants' `ChunkConsumerRegistryImpl` containers subscribe to the *same physical topic* under
different consumer groups — Kafka delivers every message to both groups. `ChunkMessageHandlerImpl`
routes each message using its own embedded `tenantCode` field (not the container's assumed tenant),
so a stray delivery still lands in the *correct* tenant's database — but it also means that message
gets processed, and its outbound HTTP call fired, **twice** (once per colliding container). This
isn't a new bug introduced by the implementation; it's a direct consequence of the topic-naming gap
this document already flagged. Closing it needs either a global topic-name-uniqueness rule enforced
on the diy-upload-services admin side, or a tenant-prefixed topic-naming convention — out of scope
for this pass.
