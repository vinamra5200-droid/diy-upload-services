package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.BatchUploadResultRow;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchUploadResultRowRepository extends JpaRepository<BatchUploadResultRow, String> {

    Page<BatchUploadResultRow> findByBatchIdOrderByRowNumberAsc(UUID batchId, Pageable pageable);

    /**
     * Unpaged, for {@code ValidatedResultS3Exporter} — a lakh-row batch must never sit fully in
     * JVM heap while its CSV export is built, so this is consumed inside one read-only
     * transaction and streamed straight to disk instead of collected into a {@code List}.
     */
    Stream<BatchUploadResultRow> streamByBatchIdOrderByRowNumberAsc(UUID batchId);
}
