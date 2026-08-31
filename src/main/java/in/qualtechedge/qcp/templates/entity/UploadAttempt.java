package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import in.qualtechedge.qcp.templates.enums.UploadDecision;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * One row per file a maker uploads for validation ({@code upload_attempts})
 * (upload-api-contract.md §2). {@code summary}/{@code issues} freeze the validation outcome once
 * {@code batchId}'s Kafka round-trip completes (see {@code BatchValidationResultServiceImpl}).
 */
@Entity
@Table(name = "upload_attempts")
@Getter
@Setter
@NoArgsConstructor
public class UploadAttempt {

    @Id
    @Column(name = "upload_attempt_id")
    private String uploadAttemptId;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @Column(name = "process_name", nullable = false)
    private String processName;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(name = "template_version", nullable = false)
    private String templateVersion;

    @Column(name = "maker_user_id", nullable = false)
    private String makerUserId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_format", nullable = false)
    private UploadFormatKey uploadFormat;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "original_file_checksum_sha256", nullable = false)
    private String originalFileChecksumSha256;

    @Column(name = "raw_object_key")
    private String rawObjectKey;

    @Column(name = "validated_object_key")
    private String validatedObjectKey;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadAttemptStatus status = UploadAttemptStatus.ACCEPTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String issues = "[]";

    @Enumerated(EnumType.STRING)
    private UploadDecision decision;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "maker_checker_enabled", nullable = false)
    private boolean makerCheckerEnabled;

    @Column(name = "validations_enabled", nullable = false)
    private boolean validationsEnabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
