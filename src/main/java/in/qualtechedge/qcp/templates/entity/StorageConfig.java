package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Interim object-store connection ({@code storage_configs}) — list+create+edit like
 * database/api-config, not a singleton (admin-api-contract.md §5).
 */
@Entity
@Table(name = "storage_configs")
@Getter
@Setter
@NoArgsConstructor
public class StorageConfig {

    @Id
    @Column(name = "config_id")
    private String configId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterimStoreProvider provider;

    @Column(name = "connection_label", nullable = false)
    private String connectionLabel;

    @Column(name = "connection_ref", nullable = false)
    private String connectionRef;

    /** AWS_S3 only. */
    @Column(name = "bucket_name")
    private String bucketName;

    /** AWS_S3 only — e.g. {@code ap-south-1}. */
    @Column(name = "bucket_region")
    private String bucketRegion;

    /** AWS_S3 only. Not itself secret, but rotate alongside {@link #secretAccessKey}. */
    @Column(name = "access_key_id")
    private String accessKeyId;

    /**
     * AWS_S3 only. Sensitive — never returned in full by the API; see
     * {@link in.qualtechedge.qcp.templates.utils.SecretMasking}. Stored as plain text for now;
     * encrypt at rest (e.g. pgcrypto {@code pgp_sym_encrypt} or an application-level KMS
     * envelope) before handling real production credentials.
     */
    @Column(name = "secret_access_key")
    private String secretAccessKey;

    /** AWS_S3 only — S3 endpoint hostname, e.g. {@code s3.amazonaws.com}. */
    @Column(name = "hostname")
    private String hostname;

    /** AWS_S3 only — endpoint port, typically 443. */
    @Column(name = "port")
    private Integer port;

    /** Server-managed; never set from a request (admin-api-contract.md §5.2). */
    @Column(name = "path_pattern", nullable = false)
    private String pathPattern = "diy-upload/{env}/{process_id}/…";

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
