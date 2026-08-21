package in.qualtechedge.qcp.templates.entity.tenant.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import in.qualtechedge.qcp.templates.entity.common.ApiClientPrincipal;
import in.qualtechedge.qcp.templates.entity.common.RolePrincipal;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tenant-scoped API client — maps to {@code api_clients} in each tenant DB. */
@Entity
// No schema: a tenant database holds one tenant, so the tables sit in public. The
// admin-side entity for the same concept is mapped to the auth schema, which is what
// keeps the two distinct while one EntityManagerFactory maps both.
@Table(name = "api_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantApiClient implements ApiClientPrincipal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", unique = true, nullable = false, updatable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email_id", nullable = false, unique = true, length = 255)
    private String emailId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "apiClient", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<TenantApiClientUserRole> roleAssignments = new HashSet<>();

    @Transient
    @Override
    public Set<RolePrincipal> getApiClientUserRoles() {
        Set<RolePrincipal> roles = new HashSet<>();
        if (roleAssignments != null) {
            for (TenantApiClientUserRole acur : roleAssignments) {
                if (acur.getStatus() != null && acur.getStatus() == 1) {
                    roles.add(acur.getRole());
                }
            }
        }
        return roles;
    }
}
