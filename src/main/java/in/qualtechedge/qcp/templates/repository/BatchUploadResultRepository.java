package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchUploadResultRepository extends JpaRepository<BatchUploadResult, UUID> {

    /**
     * Atomic claim for one batch: the {@code ON CONFLICT DO NOTHING} makes this the single point
     * where concurrent or duplicate deliveries of the same {@code batchId} (validation-service
     * retries, at-least-once delivery) are told apart from the one that gets to do the work —
     * whichever caller's insert actually lands a row (return value 1) owns processing this batch;
     * every other caller (return value 0) treats it as an already-handled no-op. Postgres resolves
     * the race itself; no application-level lock is needed.
     */
    @Modifying
    @Query(value = """
            INSERT INTO batch_upload_results
                (batch_id, process_id, template_id, status, total_rows_received, passed_count, failed_count)
            VALUES (:batchId, :processId, :templateId, :status, :totalRowsReceived, :passedCount, :failedCount)
            ON CONFLICT (batch_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("batchId") UUID batchId, @Param("processId") String processId,
            @Param("templateId") String templateId, @Param("status") String status,
            @Param("totalRowsReceived") Integer totalRowsReceived, @Param("passedCount") Integer passedCount,
            @Param("failedCount") Integer failedCount);
}
