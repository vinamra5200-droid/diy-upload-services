package in.qualtechedge.qcp.templates.multitenancy.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tenant registry row ({@code tenant.tenants} in the system DB — QCC Multi-Tenancy §4).
 * <p>
 * The registry stores only the connection string ({@code db_url}); credentials come from the
 * {@code TenantCredentialProvider} and are never persisted here. Lives exclusively in the
 * system (superadmin) database — tenant databases hold no copy of the registry.
 */
@Entity
@Table(name = "tenants", schema = "tenant")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    /** Registry status value for an active, routable tenant. */
    public static final int STATUS_ACTIVE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Lowercase tenant identifier — matches the subdomain segment and the MDC/log-file key. */
    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    /** JDBC URL of this tenant's isolated database. */
    @Column(name = "db_url", nullable = false, length = 500)
    private String dbUrl;

    /** 1 = active (routable), 0 = inactive. */
    @Column(nullable = false)
    private Integer status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
