# Database and Flyway Setup

## Database Configuration

### Profile-Specific Database Settings

Configure database connections for each environment in your profile-specific YAML files:

#### application-local.yaml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/[service-name]-db
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

#### application-dev.yaml / application-uat.yaml / application-prod.yaml

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

## Flyway Migration Setup

### POM.xml Dependencies

Add Flyway dependencies to your `pom.xml`:

```xml
<!-- ==================== Database Migration ==================== -->
<!-- Flyway for database schema versioning and migration
     (Spring Boot 4: the starter is required — flyway-core alone no longer activates auto-configuration) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<!-- Flyway with PostgreSQL -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Note (Spring Boot 4.x)**: Boot 4 modularized auto-configuration. With only `org.flywaydb:flyway-core` on the classpath the migrations silently never run (Hibernate then fails schema validation). Use `spring-boot-starter-flyway`.

### Flyway Configuration

Add Flyway configuration to `application.yaml`:

```yaml
# Flyway configuration
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
    out-of-order: false
```

**Note**: Use `baseline-on-migrate: true` only when introducing Flyway to an existing non-empty schema.

## Database Migration Rules (Flyway)

### Migration Location

Store all versioned migration scripts under `src/main/resources/db/migration`.

### File Organization

- **One file per table**: Each table should have its own migration script for clarity and maintainability.

### Versioning Scheme

Use semantic versioning with underscores: `V{major}_{minor}_{patch}__{description}.sql`

- **V1_0_x** — Schema creation, table creation, and ALTER statements (DDL)
- **V1_1_x** — Insert/seed data scripts (DML)
- **V1_2_x** — Future data patches or corrections

### Table Number Alignment

The patch number (x) should match the table's creation order:

| Patch | Table |
| ----- | ------- |
| 0 | Schemas & extensions (auth, email, sms) |
| 1 | auth.users |
| 2 | auth.roles |
| 3 | auth.api_clients |
| 4 | auth.user_tokens |
| 5 | auth.api_client_tokens |
| 6 | auth.activity_logs |
| 7 | email.email_logs |
| 8 | email.email_log_status |
| 9 | email.email_log_details |
| 10 | email.mail_senders |
| 11 | sms.sms_logs |
| 12 | sms.sms_log_status |
| 13 | notification.transaction_logs |
| 14+ | Future tables |

**Example**: To insert seed data into auth.roles, use `V1_1_2__seed_roles.sql` (minor=1 for inserts, patch=2 for roles table).

### Indexes

- Always add indexes for columns used in WHERE, JOIN, or ORDER BY clauses
- Include a comment explaining the index purpose

### Comments

Every migration script must include:

- A header comment block describing the schema/table
- Inline comments for non-obvious columns (e.g., discriminators, status codes)

### Foreign Keys

When tables have circular dependencies:

- Create tables first without FKs
- Add constraints via ALTER in a later migration

### Migration File Examples

**Schema Creation (V1_0_0__create_app_schema.sql):**

```sql
-- V1_0_0__create_app_schema.sql
-- Purpose: Create application schema and core tables

-- Create app schema
CREATE SCHEMA IF NOT EXISTS app;

-- Create example table
CREATE TABLE app.example_entity (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create index for name lookup
CREATE INDEX idx_example_entity_last_name ON app.example_entity(last_name);
```

**Reference Data (V1_1_2__seed_roles.sql):**

```sql
-- V1_1_2__seed_roles.sql
-- Purpose: Insert default roles into app.roles

INSERT INTO app.roles (code, name, created_at, updated_at) VALUES
('ADMIN', 'Administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('USER', 'Standard User', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

### Migration File Template

```sql
-- V1_0_1__create_initial_tables.sql
-- Purpose: Create initial database tables for [service-name]

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create example table
CREATE TABLE example_entity (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create indexes
CREATE INDEX idx_example_entity_name ON example_entity(name);
CREATE INDEX idx_example_entity_created_at ON example_entity(created_at);

-- Add audit trigger function (if needed)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add audit trigger
CREATE TRIGGER update_example_entity_updated_at
    BEFORE UPDATE ON example_entity
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

## Flyway Best Practices

### Migration Rules

1. **Always use forward-only migrations** - Don't use `undo` migrations
2. **Use defensive SQL where appropriate** - Use `IF NOT EXISTS` for schemas, extensions, or other objects where safe and meaningful
3. **Keep migrations small** - One logical change per migration file

### Seed Data Guidance

- Keep shared reference data in normal versioned Flyway migrations.
- Avoid environment-specific versioned migrations where possible.
- For local-only or dev-only seed data, use a separately controlled mechanism such as:
  - Profile-based bootstrap logic
  - Explicitly executed development seed scripts
  - Test fixtures for automated testing

### Running Flyway

**Manual migration:**

```bash
mvn flyway:migrate
```

**Clean and rebuild (local development only):**

```bash
mvn flyway:clean flyway:migrate
```

**Warning**: Never run `flyway:clean` in shared, UAT, staging, or production environments.

**Check migration status:**

```bash
mvn flyway:info
```

## Database Connection Pooling

### HikariCP Configuration

Add HikariCP settings to the appropriate profile-specific YAML file based on environment needs:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      leak-detection-threshold: 60000
```
