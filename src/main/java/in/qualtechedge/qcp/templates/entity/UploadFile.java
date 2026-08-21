package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
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
 * One row per raw file upload to the interim object store ({@code upload_files}) — tracks the
 * checksum (duplicate detection) and status (pending/inProgress/completed/failed counts) for
 * {@link in.qualtechedge.qcp.templates.service.S3UploadService}. Distinct from the not-yet-built
 * {@code upload_attempts} validation-pipeline table (upload-api-contract.md) — this one tracks
 * only the storage step.
 */
@Entity
@Table(name = "upload_files")
@Getter
@Setter
@NoArgsConstructor
public class UploadFile {

    @Id
    @Column(name = "upload_id")
    private String uploadId;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "e_tag")
    private String etag;

    @Column(name = "job_id")
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadFileStatus status = UploadFileStatus.pending;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
