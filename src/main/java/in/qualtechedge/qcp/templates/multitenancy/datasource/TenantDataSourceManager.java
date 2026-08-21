package in.qualtechedge.qcp.templates.multitenancy.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import in.qualtechedge.qcp.templates.multitenancy.config.MultiTenancyProperties.TenantCredentials;
import in.qualtechedge.qcp.templates.multitenancy.credentials.TenantCredentialProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Manages one HikariCP pool per tenant database (QCC Multi-Tenancy §4).
 * <p>
 * Pools are registered at startup by {@code TenantStartupInitializer} and lazily for tenants
 * added to the registry afterwards — a new tenant needs no application restart. A tenant's pool
 * can only reach that tenant's database; there is no code path for a cross-tenant connection.
 * <p>
 * NOTE: reads the registry with a plain {@link JdbcTemplate} on the system datasource to avoid
 * the circular dependency TenantConnectionProvider → TenantDataSourceManager → TenantRepository
 * (JPA) → EntityManagerFactory → TenantConnectionProvider.
 */
@Slf4j
@Component
public class TenantDataSourceManager {

    private static final String FIND_ACTIVE_BY_SHORT_CODE_SQL =
            "SELECT short_code, db_url FROM tenant.tenants "
                    + "WHERE lower(short_code) = lower(?) AND status = 1 AND db_url IS NOT NULL";

    private static final RowMapper<TenantConnectionInfo> TENANT_ROW_MAPPER = (rs, rowNum) ->
            new TenantConnectionInfo(rs.getString("short_code"), rs.getString("db_url"));

    private final JdbcTemplate jdbcTemplate;
    private final TenantCredentialProvider credentialProvider;
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceManager(DataSource systemDataSource, TenantCredentialProvider credentialProvider) {
        this.jdbcTemplate = new JdbcTemplate(systemDataSource);
        this.credentialProvider = credentialProvider;
    }

    /**
     * Returns the tenant's datasource, lazily registering a pool from the registry when the
     * tenant was onboarded after startup. Returns {@code null} when the tenant is unknown.
     */
    public DataSource getDataSource(String tenantCode) {
        DataSource dataSource = tenantDataSources.get(tenantCode);
        if (dataSource == null) {
            log.info("No datasource pool for tenant '{}' yet — attempting lazy registration from registry", tenantCode);
            List<TenantConnectionInfo> results =
                    jdbcTemplate.query(FIND_ACTIVE_BY_SHORT_CODE_SQL, TENANT_ROW_MAPPER, tenantCode);
            results.forEach(info -> registerFromRegistry(info.shortCode(), info.dbUrl()));
            dataSource = tenantDataSources.get(tenantCode);
        }
        return dataSource;
    }

    /** Registers a pool for the tenant using credentials from the {@code TenantCredentialProvider}. */
    public void registerFromRegistry(String tenantCode, String dbUrl) {
        TenantCredentials credentials = credentialProvider.getCredentials(tenantCode).orElse(null);
        if (credentials == null) {
            log.error("No DB credentials configured for tenant '{}' — pool not registered", tenantCode);
            return;
        }
        addTenantDataSource(tenantCode, dbUrl, credentials.dbUsername(), credentials.dbPassword());
    }

    /** Creates and registers the HikariCP pool for a tenant database (idempotent). */
    public void addTenantDataSource(String tenantCode, String dbUrl, String dbUsername, String dbPassword) {
        if (tenantDataSources.containsKey(tenantCode)) {
            log.info("Datasource pool for tenant '{}' already registered", tenantCode);
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setPoolName("TenantPool-" + tenantCode);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setDriverClassName("org.postgresql.Driver");

        tenantDataSources.put(tenantCode, new HikariDataSource(config));
        log.info("Registered datasource pool for tenant: {}", tenantCode);
    }

    public boolean hasTenant(String tenantCode) {
        return tenantDataSources.containsKey(tenantCode);
    }

    private record TenantConnectionInfo(String shortCode, String dbUrl) {
    }
}
