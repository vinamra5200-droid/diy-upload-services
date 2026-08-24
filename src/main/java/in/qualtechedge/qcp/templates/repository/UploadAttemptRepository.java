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

    /** Pre-check before the {@code upload_attempts_one_active_per_process_uidx} DB backstop. */
    Optional<UploadAttempt> findFirstByProcessIdAndStatusNotIn(String processId, Collection<UploadAttemptStatus> excludedStatuses);

    List<UploadAttempt> findByMakerUserIdOrderByCreatedAtDesc(String makerUserId);

    /** Resolves the owning attempt for a batch-validation-completed Kafka event. */
    Optional<UploadAttempt> findByBatchId(UUID batchId);

    /** Candidates for the timeout reaper — filtered against each row's own {@code timeoutMinutes} in Java. */
    List<UploadAttempt> findByStatusIn(Collection<UploadAttemptStatus> statuses);
}
