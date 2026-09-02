package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadSubmission;
import in.qualtechedge.qcp.templates.enums.SubmissionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadSubmissionRepository
        extends JpaRepository<UploadSubmission, String>, JpaSpecificationExecutor<UploadSubmission> {

    List<UploadSubmission> findByMakerUserIdOrderByCreatedAtDesc(String makerUserId);

    /** Checker inbox — every waiting submission except the checker's own (four-eyes at the query level). */
    List<UploadSubmission> findByStatusAndMakerUserIdNotOrderByCreatedAtDesc(SubmissionStatus status, String makerUserId);

    List<UploadSubmission> findByStatus(SubmissionStatus status);
}
