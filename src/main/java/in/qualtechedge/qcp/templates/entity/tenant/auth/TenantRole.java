package in.qualtechedge.qcp.templates.entity.tenant.auth;

import in.qualtechedge.qcp.templates.entity.common.RolePrincipal;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Tenant-scoped role — maps to {@code roles} in each tenant DB. */
@Entity
// No schema: a tenant database holds one tenant, so the tables sit in public. The
// admin-side entity for the same concept is mapped to the auth schema, which is what
// keeps the two distinct while one EntityManagerFactory maps both.
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRole implements RolePrincipal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "role_type", nullable = false)
    private Integer roleType;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime updatedAt;
}
