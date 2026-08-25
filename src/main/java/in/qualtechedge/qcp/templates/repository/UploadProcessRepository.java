package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadProcessRepository extends JpaRepository<UploadProcess, String>, JpaSpecificationExecutor<UploadProcess> {

    boolean existsByProcessNameIgnoreCase(String processName);

    boolean existsByProcessNameIgnoreCaseAndProcessIdNot(String processName, String processId);

    /** §1.1 — active processes permitted by the actor's {@code UploadRole.processAccess}. */
    List<UploadProcess> findByStatusAndProcessIdIn(ConfigStatus status, Collection<String> processIds);

    /** Next value of {@code process_id_seq} (V1_3_2) — backs the sequential {@code process_id}. */
    @Query(value = "SELECT nextval('process_id_seq')", nativeQuery = true)
    long nextProcessIdSequence();
}
