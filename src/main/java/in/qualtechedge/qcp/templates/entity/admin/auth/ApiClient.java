package in.qualtechedge.qcp.templates.entity.admin.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import in.qualtechedge.qcp.templates.entity.common.ApiClientPrincipal;
import in.qualtechedge.qcp.templates.entity.common.RolePrincipal;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Admin-scope API client — maps to {@code auth.api_clients} in the system DB. */
@Entity
@Table(name = "api_clients", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiClient implements ApiClientPrincipal {

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
    private Set<ApiClientUserRole> roleAssignments = new HashSet<>();

    @Transient
    @Override
    public Set<RolePrincipal> getApiClientUserRoles() {
        Set<RolePrincipal> roles = new HashSet<>();
        if (roleAssignments != null) {
            for (ApiClientUserRole acur : roleAssignments) {
                if (acur.getStatus() != null && acur.getStatus() == 1) {
                    roles.add(acur.getRole());
                }
            }
        }
        return roles;
    }
}
