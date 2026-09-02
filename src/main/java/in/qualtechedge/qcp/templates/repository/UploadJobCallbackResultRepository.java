package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadJobCallbackResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadJobCallbackResultRepository extends JpaRepository<UploadJobCallbackResult, String> {

    /**
     * Atomic claim for one job: the {@code ON CONFLICT DO NOTHING} makes this the single point
     * where concurrent or duplicate deliveries of the same {@code jobId} (consumer-callback-service
     * retries, at-least-once delivery) are told apart from the one that gets to do the work —
     * whichever caller's insert actually lands a row (return value 1) owns processing this
     * completion; every other caller (return value 0) treats it as an already-handled no-op.
     * Mirrors {@code BatchUploadResultRepository#claim}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO upload_job_callback_results
                (job_id, status, total_batches, success_count, failed_count)
            VALUES (:jobId, :status, :totalBatches, :successCount, :failedCount)
            ON CONFLICT (job_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("jobId") String jobId, @Param("status") String status,
            @Param("totalBatches") Integer totalBatches, @Param("successCount") Integer successCount,
            @Param("failedCount") Integer failedCount);
}
