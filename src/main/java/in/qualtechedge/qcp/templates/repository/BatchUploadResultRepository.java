package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.BatchUploadResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchUploadResultRepository extends JpaRepository<BatchUploadResult, UUID> {
}
