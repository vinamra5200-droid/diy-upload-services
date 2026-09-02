package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.InterimStoreProvider;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Post-load-action delivery job ({@code upload_jobs}) (upload-api-contract.md §3.2) — created
 * either directly (maker-checker disabled) or after checker acceptance (§4.3). {@code
 * submissionId}/{@code checkerUserId} stay {@code null} and {@code makerCheckerEnabled} is
 * {@code false} on the direct path.
 */
@Entity
@Table(name = "upload_jobs")
@Getter
@Setter
@NoArgsConstructor
public class UploadJob {

    @Id
    @Column(name = "job_id")
    private String jobId;

    @Column(name = "process_code", nullable = false)
    private String processCode;

    @Column(name = "process_name", nullable = false)
    private String processName;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(name = "template_version", nullable = false)
    private String templateVersion;

    @Column(name = "maker_user_id", nullable = false)
    private String makerUserId;

    @Column(name = "checker_user_id")
    private String checkerUserId;

    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "upload_attempt_id", nullable = false)
    private String uploadAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_format", nullable = false)
    private UploadFormatKey uploadFormat;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "passed_records", nullable = false)
    private int passedRecords;

    @Column(name = "failed_records", nullable = false)
    private int failedRecords;

    @Column(name = "completed_file_key", nullable = false)
    private String completedFileKey;

    @Column(name = "original_object_key")
    private String originalObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false)
    private InterimStoreProvider storageProvider;

    @Column(name = "maker_checker_enabled", nullable = false)
    private boolean makerCheckerEnabled;

    @Column(name = "original_file_checksum_sha256", nullable = false)
    private String originalFileChecksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "queue_job_ref")
    private String queueJobRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
