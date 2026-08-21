# Docker and Container Setup

## Overview

This document provides standardized Docker configuration for QCP Spring Boot microservices, including multi-stage builds, runtime configuration, and deployment best practices.

## Standard Dockerfile

### Multi-Stage Build Configuration

```dockerfile
# syntax=docker/dockerfile:1

# ========================
# --- Stage 1: Build ---
# ========================
ARG MAVEN_VERSION=3.9.14
ARG JAVA_VERSION=25

FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests=true

# ========================
# --- Stage 2: Runtime ---
# ========================
FROM eclipse-temurin:${JAVA_VERSION}-jre

# wget for the container health check (not bundled in the base JRE image)
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

RUN addgroup --system app && adduser --system --ingroup app app && \
    mkdir -p /app/logs && chown -R app:app /app

WORKDIR /app
COPY --from=build /app/target/[service-name]-*.jar /app/app.jar
COPY start.sh /start.sh
RUN chmod +x /start.sh && chown app:app /start.sh

USER app

# Expose ports (adjust based on service requirements)
# HTTPS - 9941 (for services requiring direct HTTPS)
# HTTP - 9942 (for health checks and internal services)
EXPOSE 9941
EXPOSE 9942

# Bind Spring Boot to the QCP HTTP port inside the container
# (requires server.port: ${SERVER_PORT:8080} in application.yaml)
ENV SERVER_PORT=9942

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9942/actuator/health || exit 1

ENTRYPOINT ["/start.sh"]
```

### Key Configuration Points

- **`# syntax` directive first**: The parser directive only takes effect when it is the very first line of the Dockerfile
- **Multi-stage build**: Separates build environment from runtime for smaller images
- **Java 25**: Uses QCP standard Java version
- **Maven 3.9.14**: QCP-approved Maven version
- **Skip tests in build**: Tests run separately in CI/CD pipeline
- **wget installed in runtime stage**: The `eclipse-temurin` JRE image does not bundle `wget`/`curl`; without it the health check always fails
- **Non-root user**: Container runs as non-root `app` user for security
- **Proper permissions**: Log directory owned by application user, not world-writable
- **Dependency caching**: Optimized layer caching for faster rebuilds
- **Health checks**: Built-in container health monitoring
- **Standard ports**: 9941 (HTTPS), 9942 (HTTP) for QCP services
- **`SERVER_PORT=9942`**: Binds Spring Boot to the QCP HTTP port inside the container; the base `application.yaml` must declare `server.port: ${SERVER_PORT:8080}` (8080 for plain local runs)

## Standard Startup Script

### start.sh

```bash
#!/bin/sh
set -e

echo "Starting application..."
exec java ${JAVA_OPTS} -jar /app/app.jar "$@"
```

### Script Features

- **Error handling**: `set -e` ensures script exits on any error
- **Java options**: Supports `$JAVA_OPTS` for JVM tuning
- **Parameter passing**: Forwards command-line arguments to Spring Boot
- **Clean execution**: Uses `exec` to replace shell process with Java process

## Complete Project Structure with Docker

### Full Directory Layout

```text
[service-name]/
├── src/
│   ├── main/
│   │   ├── java/in/qualtechedge/qcp/[sub-group]/
│   │   │   ├── [ServiceName]Application.java
│   │   │   ├── config/ ← Global configs (CORS, Beans, DB, etc.)
│   │   │   ├── controller/ ← REST API controllers (@RestController)
│   │   │   ├── dto/ ← Data Transfer Objects
│   │   │   │   ├── request/ ← Request DTOs
│   │   │   │   └── response/ ← Response DTOs
│   │   │   ├── entity/ ← JPA Entities
│   │   │   ├── exception/ ← Global handler + custom exceptions
│   │   │   ├── health/ ← Health check endpoints or indicators
│   │   │   ├── mapper/ ← MapStruct or manual converters
│   │   │   ├── properties/ ← @ConfigurationProperties classes
│   │   │   ├── repository/ ← JPA Repositories
│   │   │   ├── scheduler/ ← Cron or fixed-interval tasks
│   │   │   ├── security/ ← Spring Security, JWT filters
│   │   │   ├── service/
│   │   │   │   ├── [ServiceName]Service.java ← Service interfaces
│   │   │   │   └── impl/ ← Service implementations
│   │   │   ├── openapi/ ← OpenAPI documentation interfaces
│   │   │   └── utils/ ← Reusable classes, validators, helpers
│   │   └── resources/
│   │       ├── application.yaml ← Base Spring Boot config
│   │       ├── application-local.yaml ← Local development
│   │       ├── application-dev.yaml ← Development environment
│   │       ├── application-uat.yaml ← UAT environment
│   │       ├── application-prod.yaml ← Production environment
│   │       └── static/ ← Optional static files
│   └── test/
│       └── java/in/qualtechedge/qcp/[sub-group]/
│           └── ... (unit + integration tests)
├── .dockerignore
├── .gitattributes
├── .gitignore
├── Dockerfile
├── pom.xml
├── README.md
└── start.sh
```

### Docker-Specific Files

#### .dockerignore

```text
# Git
.git
.gitignore

# Maven build artifacts
target/
.mvn/
mvnw*

# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
Thumbs.db

# Logs
logs/
*.log

# Temporary files
*.tmp
*.temp

# Documentation (if not needed in build context)
README.md
docs/
```

## Docker Configuration Rules

### Build Arguments

- **MAVEN_VERSION**: Use `3.9.14` as QCP-approved version
- **JAVA_VERSION**: Use `25` as QCP standard
- **Service name**: Replace `[service-name]` in Dockerfile with actual service name

### Port Configuration

- **HTTPS**: Port `9941` (for services requiring direct HTTPS exposure)
- **HTTP**: Port `9942` (for health checks, internal services, and development)
- **Gateway services**: May only need HTTP port if behind load balancer
- **Internal services**: Consider exposing only required ports

### Volume Mounting

- **Logs**: Mount `/app/logs` for persistent log storage
- **Config**: Mount configuration files if needed for environment-specific overrides

### Environment Variables

- **JAVA_OPTS**: JVM tuning options (memory, GC settings)
- **SPRING_PROFILES_ACTIVE**: Set active profile (local, dev, uat, prod)
- **SERVER_PORT**: HTTP port the app binds to (set to `9942` in the image; defaults to `8080` outside containers)
- **Database credentials**: Use environment variables for sensitive data

### Image Tagging Strategy

- **Development**: `[service-name]:local` or `[service-name]:dev`
- **Environment-specific**: `[service-name]:uat`, `[service-name]:prod`
- **Semantic versioning**: `[service-name]:1.0.0`, `[service-name]:1.1.0`
- **Build numbers**: `[service-name]:1.0.0-build.123` (immutable)
- **Latest tag**: Use only for development, not production

### Service-Specific Structure

#### Gateway Services
Gateway services typically don't need:
- `entity/` (no database entities)
- `repository/` (no JPA repositories)
- `scheduler/` (unless scheduled tasks are required)

Focus on:
- `controller/` (API endpoints)
- `service/` (business logic)
- `security/` (authentication/authorization)
- `openapi/` (API documentation)

#### Data Services
Data services include:
- `entity/` (JPA entities)
- `repository/` (data access)
- `mapper/` (DTO conversions)
- Full database integration

#### Utility Services
Utility services may have minimal structure:
- Only required packages
- No database components if not needed
- Focus on specific functionality

## Build and Deployment

### Local Build

```bash
# Build Docker image
docker build -t [service-name]:latest .

# Run container
docker run -d \
  --name [service-name] \
  -p 9941:9941 \
  -p 9942:9942 \
  -e SPRING_PROFILES_ACTIVE=local \
  -v $(pwd)/logs:/app/logs \
  [service-name]:latest
```

### Production Deployment

```bash
# Build with specific version
docker build -t [service-name]:1.0.0 .

# Run with production settings
docker run -d \
  --name [service-name] \
  -p 9941:9941 \
  -p 9942:9942 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAVA_OPTS="-Xms512m -Xmx1024m" \
  -v /var/log/[service-name]:/app/logs \
  [service-name]:1.0.0
```

## Docker Compose Example

### docker-compose.yml

```yaml
services:
  [service-name]:
    build: .
    ports:
      - "9941:9941"
      - "9942:9942"
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - JAVA_OPTS=-Xms256m -Xmx512m
    volumes:
      - ./logs:/app/logs
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=[service-name]-db
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres  # Local development only - use secrets in production
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

## Best Practices

### Security

- **Non-root user**: Consider running as non-root user in production
- **Minimal base image**: Use JRE instead of JDK for runtime
- **Secrets management**: Use Docker secrets or environment variables for sensitive data

### Performance

- **Layer caching**: Order Dockerfile operations to maximize layer reuse
- **Multi-stage builds**: Reduce final image size
- **Resource limits**: Set appropriate memory and CPU limits

### Monitoring

- **Health checks**: Implement Docker health checks
- **Log aggregation**: Ensure logs are properly formatted and accessible
- **Metrics**: Expose Prometheus metrics via Actuator

## Troubleshooting

### Common Issues

1. **Build failures**: Check Maven dependencies and Java version compatibility
2. **Port conflicts**: Ensure ports 9941/9942 are available
3. **Permission errors**: Verify log directory permissions
4. **Memory issues**: Adjust `JAVA_OPTS` for available memory

### Debug Commands

```bash
# View container logs
docker logs [service-name]

# Enter container shell
docker exec -it [service-name] sh

# Check container status
docker ps -a

# Inspect container details
docker inspect [service-name]
```
