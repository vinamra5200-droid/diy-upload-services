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
# Matched by extension, not by artifact name: naming the artifact here means renaming the
# project breaks the image build, and it breaks at the COPY rather than anywhere near the
# pom.xml that actually changed. The build stage produces one runnable jar, so the wildcard
# is unambiguous — spring-boot:repackage leaves the plain jar as .jar.original.
COPY --from=build /app/target/*.jar /app/app.jar
COPY start.sh /start.sh
RUN chmod +x /start.sh && chown app:app /start.sh

USER app

# Expose ports (adjust based on service requirements)
# HTTPS - 9941 (for services requiring direct HTTPS)
# HTTP - 9942 (for health checks and internal services)
EXPOSE 9941
EXPOSE 9942

# Bind Spring Boot to the QCP HTTP port inside the container
# (base application.yaml uses server.port: ${SERVER_PORT:8080})
ENV SERVER_PORT=9942

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9942/actuator/health || exit 1

ENTRYPOINT ["/start.sh"]
