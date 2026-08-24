package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A maker's attempt handed to a checker for review ({@code upload_submissions})
 * (upload-api-contract.md §4.2). One row per {@code uploadAttemptId} (unique) — created only when
 * the owning template has {@code makerCheckerEnabled}.
 */
@Entity
@Table(name = "upload_submissions")
@Getter
@Setter
@NoArgsConstructor
public class UploadSubmission {

    @Id
    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "upload_attempt_id", nullable = false)
    private String uploadAttemptId;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @Column(name = "process_name", nullable = false)
    private String processName;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(name = "template_version", nullable = false)
    private String templateVersion;

    @Column(name = "maker_user_id", nullable = false)
    private String makerUserId;

    @Column(name = "maker_display_name", nullable = false)
    private String makerDisplayName;

    @Column(name = "pending_object_key", nullable = false)
    private String pendingObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false)
    private InterimStoreProvider storageProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String issues = "[]";

    @Column(name = "original_file_checksum_sha256", nullable = false)
    private String originalFileChecksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.WAITING_FOR_CHECKER;

    @Column(name = "checker_user_id")
    private String checkerUserId;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
