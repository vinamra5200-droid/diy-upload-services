# Logging Standards

**Phase 1 · Status: ✅ Active**

Logging at Qualtech is **structured, correlated, and audit-safe**. In BFSI, logs are evidence: they must be complete enough to trace any transaction and safe enough to never leak sensitive data.

---

## 1. Format — Structured JSON

- **All logs are JSON** (one event per line) via SLF4J + Logback (logstash JSON encoder).
- Human-readable console logging only in local dev.

**Standard fields (every log line):**

| Field | Description |
|-------|-------------|
| `timestamp` | ISO-8601 UTC |
| `level` | TRACE/DEBUG/INFO/WARN/ERROR |
| `service` | service name |
| `env` | environment |
| `correlationId` | request correlation ID (see §3) |
| `traceId` / `spanId` | from OpenTelemetry context |
| `userId` | authenticated principal **(pseudonymous ID, never PII)** |
| `logger` | logger name |
| `message` | event description |
| `context` | structured key-values (MDC) |

### Standard `logback-spring.xml`

Every QCP service uses this profile-switched configuration (`src/main/resources/logback-spring.xml`) — readable console locally, Logstash JSON everywhere else:

```xml
<configuration>

    <!-- LOCAL readable logs -->
    <springProfile name="local">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>
                    %d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] tenant=%X{tenant:-system} host=%X{host:-system} %logger{36} - %msg%n
                </pattern>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="STDOUT"/>
        </root>
    </springProfile>

    <!-- SERVER / DOCKER JSON logs -->
    <springProfile name="!local">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdc>true</includeMdc>
                <includeContext>true</includeContext>
                <includeStructuredArguments>true</includeStructuredArguments>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

</configuration>
```

### Pattern Breakdown (local profile)

| Pattern token | Produces | Why |
|---|---|---|
| `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}` | `2026-06-04T18:21:23.406+05:30` | ISO-8601 timestamp with milliseconds and zone offset — sortable, unambiguous |
| `%-5level` | `INFO ` / `ERROR` | Level left-aligned and padded to 5 chars so columns line up |
| `[%thread]` | `[http-nio-8080-exec-1]` | Thread name — separates concurrent requests in a scrolling console |
| `tenant=%X{tenant:-system}` | `tenant=acme-bank` | Tenant id read from **MDC**; `:-system` is the default when no tenant context is set (startup, schedulers) |
| `host=%X{host:-system}` | `host=ip-10-1-4-22` | Originating host from MDC, same `:-system` default |
| `%logger{36}` | `i.q.q.t.s.impl.ExampleServiceImpl` | Logger (class) name, abbreviated to ≤36 chars to keep lines compact |
| `%msg%n` | the log message + newline | The event itself |

**Key points:**

- `%X{key:-default}` is the MDC lookup syntax — `tenant` and `host` must be **put into MDC by a request filter** (alongside `correlationId`, §3); they are never passed manually on each log call.
- On server profiles the pattern is irrelevant: **`LogstashEncoder` emits JSON** and `includeMdc=true` lifts *every* MDC entry (`tenant`, `host`, `correlationId`, …) to top-level JSON fields automatically — same context, machine-queryable.
- `includeStructuredArguments=true` lets `log.info("…", kv("documentId", id))` add typed JSON fields when needed.
- Do not modify this file per service; it ships with the [Java template](http://10.1.4.100:4100/qcp/templates/java-springboot-template) and stays identical across QCP.

---

## 2. Log Levels — When to Use

| Level | Use for |
|-------|---------|
| `ERROR` | Failures requiring attention; unexpected exceptions; failed critical operations. |
| `WARN` | Recoverable issues, degraded behaviour, retries, deprecations. |
| `INFO` | Business-meaningful events (request received, account created, payment processed). |
| `DEBUG` | Developer diagnostics (off in production by default). |
| `TRACE` | Very fine-grained; never in production. |

- Production default level: **INFO**. No `DEBUG`/`TRACE` in production.

---

## 3. Correlation & Context (MDC)

- Every inbound request gets a **`correlationId`** (accept inbound `X-Correlation-Id`, else generate a UUID).
- Store it in **MDC** — together with `tenant` and `host` (consumed by the logback pattern, §1); propagate it to downstream calls (HTTP header) and async tasks.
- The same `correlationId` is returned to the client in the **`X-Correlation-Id` response header** (the `APIResponse` envelope body does not carry it — `api-standards.md` §3).
- Integrate with OpenTelemetry so `traceId`/`spanId` are present (`observability-standards.md`).

```java
// Inbound filter sets MDC
MDC.put("correlationId", resolveCorrelationId(request));
try {
    chain.doFilter(request, response);
} finally {
    MDC.clear();
}
```

---

## 4. What to Log

- Request start/end for meaningful operations (method, path, status, durationMs).
- Business events with stable, queryable keys.
- Errors with exception type, message, and `correlationId` (stack trace at ERROR).
- Security-relevant events (authn/authz outcomes) — see `security-standards.md` audit logging.

---

## 5. Layer Responsibilities — Where to Log What

**Controllers are thin** — they are *not* burdened with business logic, and their logging mirrors that: exactly **two `INFO` lines per endpoint** — request received, and operation completed — each carrying the identifying key(s).

```java
@GetMapping("/stage-status/{verificationId}")
public ResponseEntity<APIResponse<VerificationStagesStatusResponse>> getVerificationStageStatus(
        @PathVariable UUID verificationId) {
    log.info("Verification stage status request: verificationId={}", verificationId);
    VerificationStagesStatusResponse response = verificationService.getVerificationStageStatus(verificationId);
    log.info("Verification stage status retrieved: verificationId={}", verificationId);
    return ControllerUtil.createSuccessResponse("Verification stages status retrieved successfully", response);
}
```

| Layer | Logs | Never |
|---|---|---|
| **Controller** | Two `INFO` lines: `"<Operation> request: key={}"` on entry, `"<Operation> completed/retrieved: key={}"` before returning. Optional `DEBUG` for request detail. | Business logic, try/catch of business exceptions, failure logging |
| **Service impl / utils** | The real work → business events (`INFO`), diagnostics (`DEBUG`), recoverable issues (`WARN`), integration failures (`ERROR`). This is where most logging lives. | Logging secrets/PII (§6) |
| **Global exception handler** | The **single** failure log per request (`WARN` for expected 4xx, `ERROR` + stack trace for 5xx), with the request path. | — |
| **Repository** | Nothing — no logging in the data layer. | Any logging |

**Rules:**

- **Failure logging is centralised**: controllers never log errors — exceptions propagate to the `GlobalExceptionHandler`, which logs the failure **once** and returns the `APIResponse` error envelope. This prevents the same error appearing 3× in the logs.
- Log messages follow the **`"<Operation> <state>: key={}"`** convention with stable, queryable keys (`verificationId={}`, `documentCount={}`) — never string concatenation, always `{}` placeholders (lazy formatting).
- A controller method body is: *entry log → one service call → completion log → wrap in envelope*. If you're tempted to log intermediate steps in a controller, that step belongs in the service.

---

## 6. What NEVER to Log (BFSI-critical)

🚫 The following must **never** appear in logs:

- Passwords, secrets, API keys, tokens (access/refresh/JWT contents).
- Full PAN/card numbers, CVV, PINs.
- Full bank account numbers, government IDs (Aadhaar/SSN/PAN-card), full PII.
- Personal data beyond what is strictly necessary; mask where unavoidable (e.g., `****1234`).
- Full request/response bodies containing sensitive fields.

> Use masking/redaction utilities at the logging boundary. Mask by default; allow-list safe fields, not deny-list sensitive ones.

---

## 7. Audit Logging

- BFSI audit events (who did what, when, to which resource, outcome) are logged as **distinct, immutable audit records**, separate from diagnostic logs.
- Audit logs include actor, action, resource, timestamp, correlationId, and result — never sensitive payloads.
- Audit logs are retained per regulatory policy and are tamper-evident.

---

## 8. Operational Rules

- Logs go to **stdout/stderr** (12-factor); shipping/aggregation handled by platform (Phase 3).
- No `System.out.println` / `printStackTrace` / `console.log` in product code.
- Log volume is intentional — avoid noisy logs in hot paths; sample if necessary.
- Frontend client errors are sent structured (no PII) to the observability pipeline.

---

*Part of the Qualtech Engineering Framework — Phase 1, Development Methodology.*
