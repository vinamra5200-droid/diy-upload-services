package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Local summary of one consumer-callback-service delivery run ({@code upload_job_callback_results},
 * V1_4_13), populated once from {@code controller.UploadJobCallbackController} — never written
 * anywhere else. Mirrors {@link BatchUploadResult}'s role for diy-validation-service: batch-level
 * detail (which chunk, what HTTP status) stays in consumer-callback-service's own database, fetched
 * on demand; only the aggregate counts a maker needs to see are copied here.
 */
@Entity
@Table(name = "upload_job_callback_results")
@Getter
@Setter
@NoArgsConstructor
public class UploadJobCallbackResult {

    @Id
    @Column(name = "job_id")
    private String jobId;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_batches", nullable = false)
    private Integer totalBatches = 0;

    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private OffsetDateTime receivedAt;

    /** Set once {@code ProcessedResultS3Exporter} successfully writes the per-batch JSON export to S3. */
    @Column(name = "result_s3_bucket")
    private String resultS3Bucket;

    @Column(name = "result_s3_key")
    private String resultS3Key;
}
