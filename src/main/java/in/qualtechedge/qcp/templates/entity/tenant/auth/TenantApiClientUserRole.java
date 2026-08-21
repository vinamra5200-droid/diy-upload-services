package in.qualtechedge.qcp.templates.entity.tenant.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Junction linking tenant API clients to roles — maps to {@code api_client_user_roles} in each tenant DB. */
@Entity
// No schema: a tenant database holds one tenant, so the tables sit in public. The
// admin-side entity for the same concept is mapped to the auth schema, which is what
// keeps the two distinct while one EntityManagerFactory maps both.
@Table(name = "api_client_user_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantApiClientUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_client_id")
    private TenantApiClient apiClient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private TenantRole role;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime updatedAt;
}
