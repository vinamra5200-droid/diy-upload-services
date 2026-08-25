package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Local summary of one validation-service batch run ({@code batch_upload_results}), populated
 * once from {@code controller.BatchUploadController} — never written anywhere else.
 */
@Entity
@Table(name = "batch_upload_results")
@Getter
@Setter
@NoArgsConstructor
public class BatchUploadResult {

    @Id
    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_rows_received", nullable = false)
    private Integer totalRowsReceived = 0;

    @Column(name = "passed_count", nullable = false)
    private Integer passedCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private OffsetDateTime receivedAt;

    /** Set once {@code ValidatedResultS3Exporter} successfully writes the row-by-row CSV to S3. */
    @Column(name = "result_s3_bucket")
    private String resultS3Bucket;

    @Column(name = "result_s3_key")
    private String resultS3Key;
}
