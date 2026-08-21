package in.qualtechedge.qcp.templates.multitenancy.provisioning;

import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties.TenantCredentials;
import in.qualtechedge.qcp.templates.multitenancy.credentials.TenantCredentialProvider;
import in.qualtechedge.qcp.templates.multitenancy.datasource.TenantDataSourceManager;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

/**
 * Provisions and migrates isolated tenant databases at runtime (QCC Multi-Tenancy §4) —
 * a new tenant is a registry row + credentials + this service; no deployment, no restart.
 * <p>
 * Steps (all idempotent):
 * <ol>
 *   <li><b>Role</b> — create the tenant's DB role (or update its password) from the credential provider.</li>
 *   <li><b>Database</b> — {@code CREATE DATABASE ... OWNER tenant_role}. The tenant role owns its
 *       database and nothing else; on PostgreSQL 15+ the owner can fully use schema {@code public}.</li>
 *   <li><b>Migrate</b> — run the shared {@code db/tenant} Flyway scripts <i>as the tenant role</i>
 *       against the tenant database, so every tenant DB has an identical, versioned schema.</li>
 *   <li><b>Pool</b> — register the tenant's HikariCP pool with the datasource manager.</li>
 * </ol>
 * Requires the system datasource user to hold {@code CREATEDB} and {@code CREATEROLE}.
 * Requires PostgreSQL 15+ for ownership-based access to schema {@code public}, and behaves
 * differently from 16 onwards: see {@code GRANT_ROLE_WITH_SET}. Run the same major version
 * locally as the servers do — the gap between the two is what let a provisioning failure reach
 * a server while every local run stayed green.
 */
@Slf4j
@Service
public class TenantProvisioningService {

    private static final String TENANT_MIGRATION_LOCATION = "classpath:db/tenant";

    private static final String CHECK_DATABASE_EXISTS = "SELECT 1 FROM pg_database WHERE datname = ?";
    private static final String CHECK_ROLE_EXISTS = "SELECT 1 FROM pg_roles WHERE rolname = ?";
    /** PostgreSQL DDL does not support bind parameters — identifiers are validated, passwords escaped. */
    private static final String CREATE_ROLE = "CREATE ROLE \"%s\" WITH LOGIN PASSWORD '%s'";
    private static final String ALTER_ROLE = "ALTER ROLE \"%s\" WITH LOGIN PASSWORD '%s'";
    private static final String CREATE_DATABASE = "CREATE DATABASE \"%s\" OWNER \"%s\"";
    /** No cross-tenant access: another tenant's role cannot even open a connection to this DB. */
    private static final String REVOKE_PUBLIC_CONNECT = "REVOKE CONNECT ON DATABASE \"%s\" FROM PUBLIC";

    /*
     * PostgreSQL 16 changed what CREATEROLE gets for free.
     *
     * A non-superuser that creates a role is still granted membership in it automatically, but
     * with ADMIN TRUE and SET FALSE — it may administer the role without becoming it.
     * CREATE DATABASE ... OWNER requires becoming it, so the automatic grant is not enough and
     * the create fails with "must be able to SET ROLE". ADMIN OPTION is what lets this be fixed
     * here rather than needing a superuser.
     *
     * The plain form is the fallback: WITH SET is 16+ syntax, and before 16 membership carried
     * no such distinction, so a bare GRANT is exactly right there.
     */
    private static final String GRANT_ROLE_WITH_SET = "GRANT \"%s\" TO CURRENT_USER WITH SET TRUE";
    private static final String GRANT_ROLE = "GRANT \"%s\" TO CURRENT_USER";

    /** PostgreSQL SQLState for duplicate database — raised when CREATE DATABASE races a concurrent creation. */
    private static final String PG_SQLSTATE_DUPLICATE_DATABASE = "42P04";

    /** PostgreSQL SQLState for a syntax error — how a pre-16 server rejects WITH SET. */
    private static final String PG_SQLSTATE_SYNTAX_ERROR = "42601";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");

    private final DataSource systemDataSource;
    private final TenantCredentialProvider credentialProvider;
    private final TenantDataSourceManager tenantDataSourceManager;

    public TenantProvisioningService(DataSource systemDataSource,
                                     TenantCredentialProvider credentialProvider,
                                     TenantDataSourceManager tenantDataSourceManager) {
        this.systemDataSource = systemDataSource;
        this.credentialProvider = credentialProvider;
        this.tenantDataSourceManager = tenantDataSourceManager;
    }

    /**
     * Full onboarding for one registry tenant: role + database + Flyway + pool.
     * Idempotent — safe to call at startup for already-provisioned tenants.
     *
     * @throws TenantProvisioningException when any step fails (no credentials, SQL error, migration error)
     */
    public void onboard(Tenant tenant) {
        String tenantCode = tenant.getShortCode();
        TenantCredentials credentials = credentialProvider.getCredentials(tenantCode)
                .orElseThrow(() -> new TenantProvisioningException(
                        "No DB credentials configured for tenant '" + tenantCode + "'"));

        provisionRoleAndDatabase(tenantCode, tenant.getDbUrl(), credentials);
        migrate(tenantCode, tenant.getDbUrl(), credentials);
        tenantDataSourceManager.addTenantDataSource(
                tenantCode, tenant.getDbUrl(), credentials.dbUsername(), credentials.dbPassword());
    }

    /**
     * Makes the connected role able to SET ROLE to the tenant role, which is what
     * CREATE DATABASE ... OWNER requires of a non-superuser on PostgreSQL 16 and later.
     *
     * <p>Deliberately not fatal. A superuser already has the right and does not need the grant;
     * a role that is already a member gets a harmless no-op; and if the grant genuinely cannot
     * be made, the CREATE DATABASE below fails with a message that says so. Aborting here
     * instead would replace that specific error with a vaguer one raised a step earlier.
     */
    private void ensureCanSetRole(Connection connection, String roleName, String tenantCode) {
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute(String.format(GRANT_ROLE_WITH_SET, roleName));
            } catch (SQLException e) {
                if (!PG_SQLSTATE_SYNTAX_ERROR.equals(e.getSQLState())) {
                    throw e;
                }
                // PostgreSQL 15 or older: membership has no SET option to ask for.
                statement.execute(String.format(GRANT_ROLE, roleName));
            }
        } catch (SQLException e) {
            log.warn("[Provisioning][tenant={}] Could not take SET ROLE on '{}' ({}). "
                            + "Continuing — CREATE DATABASE will report it if this mattered.",
                    tenantCode, roleName, e.getMessage());
        }
    }

    /** Creates the tenant role (or refreshes its password) and the tenant database if missing. */
    private void provisionRoleAndDatabase(String tenantCode, String dbUrl, TenantCredentials credentials) {
        String databaseName = JdbcUrl.parse(dbUrl).database();
        String roleName = credentials.dbUsername();
        validateIdentifier(databaseName, "database name");
        validateIdentifier(roleName, "role name");

        try (Connection connection = systemDataSource.getConnection()) {
            boolean roleCreated = createOrUpdateRole(connection, roleName, credentials.dbPassword());
            log.info("[Provisioning][tenant={}] Role '{}' {}", tenantCode, roleName,
                    roleCreated ? "created" : "password refreshed");

            /*
             * Before the database, because owning it is the thing that needs it. Every onboard
             * rather than only a freshly created role: an existing tenant whose role predates
             * this call is exactly the case that would otherwise stay broken after an upgrade.
             */
            ensureCanSetRole(connection, roleName, tenantCode);

            boolean databaseCreated = createDatabaseIfNotExists(connection, databaseName, roleName, tenantCode);
            log.info("[Provisioning][tenant={}] Database '{}' {}", tenantCode, databaseName,
                    databaseCreated ? "created" : "already exists, skipping");

            // Idempotent hardening: only the owner role (and superusers) may connect to a tenant DB
            try (Statement statement = connection.createStatement()) {
                statement.execute(String.format(REVOKE_PUBLIC_CONNECT, databaseName));
            }
        } catch (SQLException e) {
            throw new TenantProvisioningException(
                    "Database provisioning failed for tenant '" + tenantCode + "'", e);
        }
    }

    /** Runs the shared db/tenant migrations against the tenant database as the tenant role. */
    private void migrate(String tenantCode, String dbUrl, TenantCredentials credentials) {
        log.info("[Provisioning][tenant={}] Running Flyway migrations on {}", tenantCode, dbUrl);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dbUrl, credentials.dbUsername(), credentials.dbPassword())
                    .locations(TENANT_MIGRATION_LOCATION)
                    // One schema, public. A tenant database holds exactly one tenant, so
                    // sub-schemas inside it restate what the database already is — and the
                    // placeholders that named them are gone with the migrations that used them.
                    .defaultSchema("public")
                    .placeholders(Map.of("tenant_code", tenantCode))
                    // The V1_0_x (schema) and V1_1_x (seed) series are independent numbering
                    // tracks, so a later-added V1_0_x migration can land with a version lower
                    // than a V1_1_x seed already applied to an existing tenant DB. Flyway's
                    // default (outOfOrder=false) then refuses it — and every migration after it
                    // — rather than risk applying it out of sequence; onboard() would then throw
                    // and this tenant would never get a datasource pool at startup. Each
                    // migration in TENANT_MIGRATION_LOCATION is still applied at most once
                    // (tracked in flyway_schema_history) regardless of this setting.
                    .outOfOrder(true)
                    .load();
            int applied = flyway.migrate().migrationsExecuted;
            log.info("[Provisioning][tenant={}] Flyway completed ({} migration(s) applied)", tenantCode, applied);
        } catch (Exception e) {
            throw new TenantProvisioningException(
                    "Flyway migration failed for tenant '" + tenantCode + "'", e);
        }
    }

    private boolean createOrUpdateRole(Connection connection, String roleName, String password) throws SQLException {
        boolean roleExists;
        try (PreparedStatement check = connection.prepareStatement(CHECK_ROLE_EXISTS)) {
            check.setString(1, roleName);
            try (ResultSet rs = check.executeQuery()) {
                roleExists = rs.next();
            }
        }
        String escapedPassword = password.replace("'", "''");
        String sql = String.format(roleExists ? ALTER_ROLE : CREATE_ROLE, roleName, escapedPassword);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        return !roleExists;
    }

    private boolean createDatabaseIfNotExists(Connection connection, String databaseName, String ownerRole,
                                              String tenantCode) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement(CHECK_DATABASE_EXISTS)) {
            check.setString(1, databaseName);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }
        }
        // CREATE DATABASE must run outside a transaction (connection is in autocommit here)
        try (Statement statement = connection.createStatement()) {
            statement.execute(String.format(CREATE_DATABASE, databaseName, ownerRole));
            return true;
        } catch (SQLException e) {
            if (PG_SQLSTATE_DUPLICATE_DATABASE.equals(e.getSQLState())) {
                log.warn("[Provisioning][tenant={}] Concurrent CREATE DATABASE detected for '{}' — treating as success",
                        tenantCode, databaseName);
                return false;
            }
            throw e;
        }
    }

    private void validateIdentifier(String value, String what) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new TenantProvisioningException("Unsafe " + what + " for provisioning: '" + value + "'");
        }
    }
}
