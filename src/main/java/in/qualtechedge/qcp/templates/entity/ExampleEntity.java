package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Example entity backing the example CRUD feature. Lives in the TENANT databases (public
 * schema) — Hibernate routes every query to the current tenant's isolated DB, so two tenants
 * calling the same endpoint read physically different tables.
 * Schema is managed by per-tenant Flyway (db/tenant/V1_0_0__create_example_entity_table.sql);
 * ddl-auto stays 'none' in multitenant services (see MultiTenantJpaConfig).
 */
@Entity
@Table(name = "example_entity")
@Getter
@Setter
@NoArgsConstructor
public class ExampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Optimistic locking counter. */
    @Version
    private Long version;
}
