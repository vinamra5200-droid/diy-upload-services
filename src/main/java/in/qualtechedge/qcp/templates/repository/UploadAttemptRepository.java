package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadAttempt;
import in.qualtechedge.qcp.templates.enums.UploadAttemptStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadAttemptRepository extends JpaRepository<UploadAttempt, String> {

    List<UploadAttempt> findByMakerUserIdOrderByCreatedAtDesc(String makerUserId);

    /** Resolves the owning attempt for a validation-completed callback (BatchUploadController). */
    Optional<UploadAttempt> findByBatchId(UUID batchId);

    /** Candidates for the timeout reaper — filtered against {@code qcp.upload.attempt-timeout-minutes} in Java. */
    List<UploadAttempt> findByStatusIn(Collection<UploadAttemptStatus> statuses);
}
