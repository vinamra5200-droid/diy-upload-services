package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.AuditEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String>, JpaSpecificationExecutor<AuditEvent> {

    /** Chain tail for prev_event_id linkage (SD §12.4 tamper-evident ordering). */
    Optional<AuditEvent> findTopByUploadAttemptIdOrderByOccurredAtDesc(String uploadAttemptId);
}
