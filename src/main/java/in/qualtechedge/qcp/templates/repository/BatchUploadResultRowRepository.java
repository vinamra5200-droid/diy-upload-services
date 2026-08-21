package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.BatchUploadResultRow;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchUploadResultRowRepository extends JpaRepository<BatchUploadResultRow, String> {

    Page<BatchUploadResultRow> findByBatchIdOrderByRowNumberAsc(UUID batchId, Pageable pageable);
}
