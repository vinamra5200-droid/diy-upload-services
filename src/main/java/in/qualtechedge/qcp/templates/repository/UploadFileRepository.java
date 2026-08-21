package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadFileRepository extends JpaRepository<UploadFile, String>, JpaSpecificationExecutor<UploadFile> {

    /** Duplicate check: an existing non-failed row for this exact file already covers this template. */
    Optional<UploadFile> findFirstByTemplateIdAndChecksumSha256AndStatusNot(
            String templateId, String checksumSha256, UploadFileStatus excludedStatus);

    /** Resolves the processId/templateId a Kafka batchId (jobId) belongs to. */
    Optional<UploadFile> findFirstByJobId(String jobId);
}
