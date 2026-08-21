# Spring Boot Project Standards

## Profile Configuration

### application.yaml Profile Setup

Add the following profile configuration to `src/main/resources/application.yaml`:

```yaml
# Application configuration
spring:
  application:
    name: [service-name]
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

# Actuator configuration
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when_authorized
      show-components: when_authorized
```

## Profiles Rules

- Maintain aligned placeholders across profiles (e.g., if `application-dev.yaml` defines a property, add the key with placeholder values in `application-uat.yaml` and `application-prod.yaml`).
- **Configuration placement:**
  - **Common values** (not changing across environments) → `application.yaml` (base config)
  - **Environment-specific values** → profile-specific files (`application-local.yaml`, `application-dev.yaml`, `application-uat.yaml`, `application-prod.yaml`)
- **Value format by environment:**
  - `application-local.yaml` → Use **actual values** for local development (e.g., `url: jdbc:postgresql://localhost:5432/[service-name]-db`)
  - `application-dev.yaml`, `application-uat.yaml`, `application-prod.yaml` → Use **`${PLACEHOLDER_NAME}`** format with UPPERCASE names that will be injected as environment variables during deployment (e.g., `url: ${DB_URL}`)
- **Example:**

  ```yaml
  # application.yaml (common, never changes)
  spring:
    jpa:
      hibernate:
        ddl-auto: validate

  # application-local.yaml (actual values)
  spring:
    datasource:
      url: jdbc:postgresql://localhost:5432/[service-name]-db
      username: postgres
      password: postgres

  # application-dev.yaml / application-uat.yaml / application-prod.yaml (placeholders)
  spring:
    datasource:
      url: ${DB_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD:default}
  ```

- **Default values:** Use `${PLACEHOLDER:default}` syntax when a fallback is needed (e.g., `port: ${SERVER_PORT:8080}`)

### Environment-Specific Configuration Files

Create additional profile-specific configuration files in `src/main/resources/`:

- **application-local.yaml** - Local development settings
- **application-dev.yaml** - Development environment configuration  
- **application-uat.yaml** - UAT environment configuration
- **application-prod.yaml** - Production environment configuration

Follow the profile-specific configuration rules in the Database and Flyway Setup document for detailed examples.

## Running with Different Profiles

**Run with specific profile:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Run with local profile (default):**

```bash
mvn spring-boot:run
```

**Run with production profile:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Project Structure with Profiles

After adding profile configurations, the project structure will be:

```text
[service-name]/
├── src/
│   ├── main/
│   │   ├── java/in/qualtechedge/qcp/[sub-group]/
│   │   │   └── [ServiceName]Application.java
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-local.yaml
│   │       ├── application-dev.yaml
│   │       ├── application-uat.yaml
│   │       └── application-prod.yaml
│   └── test/
├── .gitattributes
├── .gitignore
├── pom.xml
└── README.md
```

## Comprehensive Project Structure

Only create packages required by the service. Do not create empty packages just to match the reference structure.

Complete project structure following all QCP rules and standards:

```text
[service-name]/
├── src/
│   ├── main/
│   │   ├── java/in/qualtechedge/qcp/[sub-group]/
│   │   │   ├── [ServiceName]Application.java
│   │   │   ├── config/ ← Global configs (CORS, Beans, DB, etc.)
│   │   │   ├── constant/ ← Application constants
│   │   │   ├── controller/ ← REST API controllers (@RestController)
│   │   │   ├── dto/
│   │   │   │   ├── request/ ← Request DTOs with validation
│   │   │   │   └── response/ ← Response DTOs
│   │   │   ├── entity/ ← JPA entity classes
│   │   │   ├── enums/ ← Application enums (`enum` is a reserved Java keyword)
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
├── .gitattributes
├── .gitignore
├── pom.xml
└── README.md
```

## Project Structure Rules

### Import Rules

- Always use fully qualified imports; never use `*`. Example: prefer `import org.springframework.web.bind.annotation.DeleteMapping;` over the wildcard import.

### DTO Rules

- Keep DTOs under a `dto` package with separate `request` and `response` subpackages. Example: for `controller.MyController#processData`, use `dto.request.DataRequest` and `dto.response.DataResponse`.

### DTO Request Rules

- Use `record` for request DTOs by default.
- Apply `jakarta.validation.constraints` annotations such as `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, and `@Email`.
- Use `@JsonInclude(JsonInclude.Include.NON_NULL)` only when needed.

### DTO Response Rules

- Use `record` for response DTOs by default.
- Use `@JsonInclude(JsonInclude.Include.NON_NULL)` only when needed.

### Lombok Rules

- Use Lombok in concrete components such as services, schedulers, configuration classes, and helpers where it improves readability.
- Add `@Slf4j` to controllers, service implementations, schedulers, and other concrete components where logging is needed.
- Avoid unnecessary logging in the repository layer.

### Controller Rules

- Place all controllers under `controller`. Use `@RestController` for API controllers (e.g., `controller.MyController`). Controllers serving only web views can use `@Controller`.
- Put `@RequestMapping` on the class to define the base path.
- Apply authorization consistently using Spring Security.
- Use `@PreAuthorize` for method-level authorization where applicable.
- Use `@RequiredArgsConstructor` for dependency injection.
- Use `@Slf4j`; each endpoint logs exactly **two `INFO` lines** — request received on entry and operation completed before returning, each carrying identifying keys (e.g., `log.info("Verification details request: verificationId={}", verificationId);`). Failures are logged once by the global exception handler, never in controllers. See `logging-standards.md`, section 5.
- Avoid handling business exceptions inside controllers.
- Let exceptions propagate to the global exception handler unless endpoint-specific handling is required.
- Keep controller methods thin: delegate to the service and return the result.
- Required API response pattern: `ResponseEntity<APIResponse<T>>` — the QCP-standard envelope is a **locked contract**; the record definition, field reference and examples live in the API Standards document (`api-standards.md`, section 3).
- Prefer constructor injection over field injection.

### Service Rules

- **Always create service interfaces** for core business logic (e.g., `MyService` interface)
- **Create implementation classes** in `service/impl/` subpackage (e.g., `service/impl/MyServiceImpl`)
- Use `@Service` annotation on implementation classes
- Use `@RequiredArgsConstructor` for dependency injection in implementations
- Add `@Slf4j` to service implementations for logging
- Keep business logic in service implementations, not in controllers
- Service interfaces should define the contract, implementations contain the actual logic

### OpenAPI Documentation Rules

- Keep OpenAPI documentation contracts in a separate `openapi` package.
- Define a documentation interface (for example, `openapi.MyDocumentation`) and let the controller implement it.
- Place OpenAPI documentation annotations inside the interface.
- Keep Jakarta validation, security, HTTP status, and request mapping annotations inside the controller, not the Swagger interface.

### Mapper Rules

- Place mapper classes in a dedicated `mapper` package.

### Properties Rules

- Use a `properties` package for configuration bindings.
- Use `@ConfigurationProperties(prefix = "external-service")` for grouped configuration.
- Use either a standard POJO or a record, based on the project's Spring Boot configuration binding approach.

### Utils Rules

- Place generic helpers in a `utils` package.
- Throw custom exceptions from utilities; let the global exception handler manage them.

## Logging Configuration

### Logback Configuration

Create `src/main/resources/logback-spring.xml` with profile-specific logging:

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

### POM.xml Dependency

Add the Logstash encoder dependency to your `pom.xml`:

```xml
<!-- ==================== Logging ==================== -->
<!-- Logstash encoder for structured JSON logging in production -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>9.0</version>
</dependency>
```

### Logging Features

- **Local Development**: Human-readable console logs with tenant and host context
- **Production/Docker**: Structured JSON logs for better log aggregation and monitoring
- **MDC Support**: Includes MDC (Mapped Diagnostic Context) for distributed tracing
- **Profile-based**: Automatically switches format based on active profile
