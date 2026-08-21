# Architecture

## Overview

`java-springboot-template` is the QCP baseline Spring Boot 4 (Java 25) service. It ships one **example CRUD feature** (`/api/v1/examples`) whose only purpose is to demonstrate the QCP layering; services created from this template replace it with real features.

```text
HTTP client
   │
   ▼
ExampleController (controller/)  ←implements— ExampleDocumentation (openapi/)
   │  @Valid ExampleRequest (dto/request/)
   ▼
ExampleService → ExampleServiceImpl (service/, service/impl/)
   │                    │
   ▼                    ▼
ExampleMapper       ExampleEntityRepository (repository/)
(mapper/)               │
   │                    ▼
ExampleResponse     ExampleEntity (entity/) ── PostgreSQL (schema app, Flyway-managed)
(dto/response/)
```

Cross-cutting: `GlobalExceptionHandler` (exception/) converts exceptions to the `APIResponse<T>` envelope (locked QCP contract — api-standards §3); Logback switches between readable (local) and Logstash JSON (server) output; Actuator exposes health/info/prometheus.

## Key decisions

- **Schema owned by Flyway** — `ddl-auto: validate`; migrations follow QCP `V{major}_{minor}_{patch}` versioning.
- **Layer-first packages** with service interfaces + `impl/`, OpenAPI contracts in a separate `openapi/` package.
- **Profile model**: `local` holds real values; `dev`/`uat`/`prod` hold aligned `${ENV_VAR}` placeholders injected at deployment.
- **Container**: multi-stage build, non-root user, binds to QCP HTTP port 9942 via `SERVER_PORT`.

## Dependencies

PostgreSQL (runtime), Prometheus scraper (optional), no upstream service dependencies.
