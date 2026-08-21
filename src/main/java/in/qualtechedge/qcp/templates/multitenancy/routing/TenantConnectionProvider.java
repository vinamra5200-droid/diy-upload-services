package in.qualtechedge.qcp.templates.multitenancy.routing;

import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.datasource.TenantDataSourceManager;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Hands Hibernate a connection from the current tenant's pool (QCC Multi-Tenancy §4).
 * <p>
 * {@code system} → the primary (system/superadmin) datasource; any other identifier → that
 * tenant's isolated database pool. An unknown tenant identifier fails hard — there is
 * deliberately no fallback to another database, so a cross-tenant connection is impossible
 * by construction.
 */
@Slf4j
@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DataSource systemDataSource;
    private final TenantDataSourceManager tenantDataSourceManager;

    public TenantConnectionProvider(DataSource systemDataSource,
                                    @Lazy TenantDataSourceManager tenantDataSourceManager) {
        this.systemDataSource = systemDataSource;
        this.tenantDataSourceManager = tenantDataSourceManager;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return systemDataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (tenantIdentifier == null || HostContext.SYSTEM_TENANT.equals(tenantIdentifier)) {
            return systemDataSource.getConnection();
        }

        DataSource tenantDataSource = tenantDataSourceManager.getDataSource(tenantIdentifier);
        if (tenantDataSource == null) {
            // No fallback by design: failing is safer than ever touching another tenant's data
            throw new SQLException("No datasource registered for tenant '" + tenantIdentifier + "'");
        }
        log.debug("Connection acquired from isolated pool for tenant: {}", tenantIdentifier);
        return tenantDataSource.getConnection();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
