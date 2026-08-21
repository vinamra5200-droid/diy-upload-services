# API Standards

**Phase 1 · Status: ✅ Active**

This document defines how Qualtech designs HTTP/REST APIs. Contracts are designed **before** implementation. APIs are products: stable, versioned, documented, and secure.

---

## 1. Style & Conventions

- **REST over HTTP/JSON** is the default. (GraphQL/gRPC require Architect approval.)
- **Resource-oriented URLs**, plural nouns, `kebab-case`:
  - `GET /api/v1/savings-accounts`
  - `GET /api/v1/savings-accounts/{accountId}`
  - `POST /api/v1/savings-accounts/{accountId}/transactions`
- **No verbs in paths.** Use HTTP methods for actions. (Rare action endpoints use `:action` suffix only with approval.)
- JSON field names: **`camelCase`**. Dates/times: **ISO-8601 / RFC 3339, UTC** (`2026-05-31T10:15:30Z`). Money: object with `amount` (string decimal) + `currency` (ISO-4217).

### URL Naming Rules

- **Every letter in a URL path is lowercase.** No uppercase, no `camelCase`, no `snake_case`.
- **Multi-word segments are separated by hyphens** (`kebab-case`).
- Path **parameters** (placeholders) stay `camelCase` inside braces: `{accountId}`, `{documentId}`.
- **Query parameter names** are `camelCase` (matching JSON field names): `?pageSize=20&sortBy=createdAt`.

| ✅ Correct | ❌ Wrong |
|---|---|
| `/api/v1/kyc-documents` | `/api/v1/kycDocuments` (camelCase) |
| `/api/v1/kyc-documents/{documentId}` | `/api/v1/KYC-Documents` (uppercase) |
| `/api/v1/account-statements` | `/api/v1/account_statements` (underscore) |

---

## 2. HTTP Methods & Status Codes

| Method | Use | Success | Idempotent |
|--------|-----|---------|-----------|
| GET | Read | 200 | Yes |
| POST | Create / action | 201 (created) / 200 | No (use idempotency key for money movement) |
| PUT | Full replace | 200 / 204 | Yes |
| PATCH | Partial update | 200 | No |
| DELETE | Remove | 204 | Yes |

| Status | Meaning |
|--------|---------|
| 400 | Validation / malformed request |
| 401 | Unauthenticated |
| 403 | Authenticated but not authorized |
| 404 | Resource not found |
| 409 | Conflict (e.g., version, duplicate) |
| 422 | Semantically invalid business request |
| 429 | Rate limited |
| 500 | Unexpected server error (never leak internals) |

---

## 3. Standard Response Envelope — `APIResponse<T>` (final)

Every QCP endpoint — success **and** error — returns the standard `APIResponse<T>` envelope. This contract is **locked**; services must not invent their own wrappers.

```java
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record APIResponse<T>(
        Status status,
        Integer statusCode,
        String message,
        T data,
        String errorCode,
        String errorMessage,
        String path,
        List<ErrorDetail> errors,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    // Static factory for success response with data
    public static <T> APIResponse<T> success(Integer statusCode, String message, T data) {
        return new APIResponse<>(
                Status.SUCCESS, statusCode, message, data,
                null, null, null, null,
                LocalDateTime.now()
        );
    }

    // Static factory for error response
    public static <T> APIResponse<T> error(Integer statusCode, String errorMessage) {
        return new APIResponse<>(
                Status.ERROR, statusCode, null, null,
                null, errorMessage, null, null,
                LocalDateTime.now()
        );
    }

    public enum Status {
        SUCCESS, ERROR
    }

    public record ErrorDetail(
            String field,
            String errorCode,
            String errorMessage
    ) {
    }
}
```

Place the record in the service's `dto/response` package; controllers return `ResponseEntity<APIResponse<T>>`.

### Field Reference

| Field | Type | When present | Meaning |
|---|---|---|---|
| `status` | `SUCCESS` \| `ERROR` | always | Outcome discriminator |
| `statusCode` | integer | always | HTTP status code, mirrored in the body |
| `message` | string | success | Human-readable success message |
| `data` | `T` | success | The payload (object or list) |
| `errorCode` | string | error | Stable machine-readable code (e.g. `QT-VAL-001`) |
| `errorMessage` | string | error | Human-readable error summary |
| `path` | string | error | Request path that produced the error |
| `errors` | `ErrorDetail[]` | validation errors | Per-field details: `field`, `errorCode`, `errorMessage` |
| `timestamp` | string | always | `yyyy-MM-dd HH:mm:ss` |

`@JsonInclude(NON_NULL)` keeps the wire format lean — fields that don't apply are omitted, so success bodies never carry error fields and vice versa.

### Examples

**Success (200/201):**

```json
{
  "status": "SUCCESS",
  "statusCode": 200,
  "message": "Document verified",
  "data": { "documentId": "9f3c…", "state": "VERIFIED" },
  "timestamp": "2026-06-04 15:20:35"
}
```

**Validation error (400):**

```json
{
  "status": "ERROR",
  "statusCode": 400,
  "errorCode": "QT-VAL-001",
  "errorMessage": "Validation failed",
  "path": "/api/v1/kyc-documents",
  "errors": [
    { "field": "documentNumber", "errorCode": "QT-VAL-010", "errorMessage": "must not be blank" }
  ],
  "timestamp": "2026-06-04 15:20:35"
}
```

### Rules

- **Never** return raw stack traces, SQL, or internal messages to clients — the global exception handler converts every exception into this envelope.
- Correlation/trace IDs travel in **headers** (`X-Correlation-Id`), not in the envelope body; they must match the logging correlation ID for traceability.
- For collection endpoints, `data` carries the list and its pagination metadata (see §5).

---

## 4. Versioning

- **URI versioning:** `/api/v1/...`. Major version increments for breaking changes only.
- Backward-compatible changes (new optional fields/endpoints) do **not** bump the version.
- Deprecation: announce, set `Deprecation` / `Sunset` headers, support old version through a defined window.

---

## 5. Pagination, Filtering, Sorting

- Pagination is **mandatory** for collection endpoints. Default `size=20`, max `size=100`.
- Query params: `?page=0&size=20&sort=createdAt,desc&status=ACTIVE`.
- Never return unbounded lists.
- Paginated payloads live inside the envelope's `data`:

```json
{
  "status": "SUCCESS",
  "statusCode": 200,
  "message": "OK",
  "data": {
    "content": [ /* items */ ],
    "page": { "number": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
  },
  "timestamp": "2026-06-04 15:20:35"
}
```

---

## 6. Idempotency & Concurrency

- **Money-movement / critical POSTs** require an `Idempotency-Key` header; server stores and replays the result.
- Use **optimistic concurrency** (`ETag`/`If-Match` or version field) for updates; return `409` on conflict.

---

## 7. Security (API)

- All endpoints require authentication unless explicitly public (documented).
- OAuth2 / OIDC bearer tokens (JWT). Enforce authorization per `security-standards.md`.
- Rate limiting and input validation on every endpoint. Use HTTPS only.
- Never expose internal IDs or data that enables enumeration; never trust client-provided ownership claims.

---

## 8. Documentation (OpenAPI)

- Every API documented via **springdoc-openapi (OpenAPI 3.1)** — generated from code, not hand-maintained.
- Each operation: summary, description, request/response schemas, error responses, examples, auth requirements.
- OpenAPI spec published with the service and used for contract tests.

---

## 9. Contracts First

- Design and review the contract (OpenAPI) **before** implementation.
- Breaking changes require a new version and consumer sign-off.
- Contract tests verify provider conformance (`testing-standards.md`).

---

*Part of the Qualtech Engineering Framework — Phase 1, Development Methodology.*
