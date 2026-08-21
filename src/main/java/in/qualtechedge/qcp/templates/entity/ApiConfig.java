package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ApiConfigMethod;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Reusable outbound HTTP call definition ({@code api_configs}) (admin-api-contract.md §7).
 * {@code queryParams}/{@code headers}/{@code auth} are JSONB columns kept as raw JSON text here
 * ({@code @JdbcTypeCode(SqlTypes.JSON)} on a {@code String} field) and converted to/from typed
 * DTOs in {@link in.qualtechedge.qcp.templates.mapper.ApiConfigMapper} via
 * {@link in.qualtechedge.qcp.templates.utils.JsonColumnMapper}.
 */
@Entity
@Table(name = "api_configs")
@Getter
@Setter
@NoArgsConstructor
public class ApiConfig {

    @Id
    @Column(name = "config_id")
    private String configId;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiConfigMethod method = ApiConfigMethod.GET;

    @Column(nullable = false, columnDefinition = "text")
    private String uri;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_params", nullable = false, columnDefinition = "jsonb")
    private String queryParams = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String headers = "[]";

    @Column(nullable = false, columnDefinition = "text")
    private String body = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String auth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigStatus status = ConfigStatus.draft;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
