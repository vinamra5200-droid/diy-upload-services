package in.qualtechedge.qcp.templates.multitenancy.routing;

import in.qualtechedge.qcp.templates.TemplateServiceApplication;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration for database-per-tenant multi-tenancy (QCC Multi-Tenancy §4).
 * <p>
 * One {@code EntityManagerFactory} serves every tenant: Hibernate asks
 * {@link TenantIdentifierResolver} for the current tenant and {@link TenantConnectionProvider}
 * for a connection from that tenant's pool. There is no schema switching and no
 * {@code tenant_id} column — isolation is the database itself.
 * <p>
 * Note: {@code spring.jpa.hibernate.ddl-auto} must stay {@code none} — one EMF maps both the
 * system entities (tenant registry) and the tenant entities, so schema validation cannot run
 * against a single database. Flyway owns the schema of both ({@code db/migration}, {@code db/tenant}).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableTransactionManagement
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
public class MultiTenantJpaConfig {

    /**
     * The application's root package, taken from the class Spring already treats as the root
     * rather than written out as a literal.
     *
     * <p>A literal is the worst kind of thing to leave in a template: renaming the package
     * compiles perfectly and then finds no entities at run time, surfacing much later as an
     * unrelated "Not a managed type". Reading it from the {@code @SpringBootApplication} class
     * cannot drift, because that annotation already defines the same root for component
     * scanning — the two are now the same fact stated once.
     */
    private static final String PACKAGES_TO_SCAN =
            TemplateServiceApplication.class.getPackageName();

    private final JpaProperties jpaProperties;
    private final HibernateProperties hibernateProperties;
    private final DataSource dataSource;

    @Bean
    public JpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        return adapter;
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            JpaVendorAdapter jpaVendorAdapter,
            MultiTenantConnectionProvider<String> multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver) {

        // Start from the spring.jpa.* / spring.jpa.properties.* configuration
        Map<String, Object> properties = new HashMap<>(hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(), new HibernateSettings()));

        // Hibernate multi-tenancy: tenant resolved per session, connection routed per tenant
        properties.put("hibernate.multi_tenant_connection_provider", multiTenantConnectionProvider);
        properties.put("hibernate.tenant_identifier_resolver", currentTenantIdentifierResolver);
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        log.info("Configuring Hibernate database-per-tenant multi-tenancy (packages: {})", PACKAGES_TO_SCAN);

        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan(PACKAGES_TO_SCAN);
        entityManagerFactory.setJpaVendorAdapter(jpaVendorAdapter);
        entityManagerFactory.setJpaPropertyMap(properties);
        return entityManagerFactory;
    }

    // The JPA transaction manager is auto-configured by Spring Boot on top of this EMF
}
