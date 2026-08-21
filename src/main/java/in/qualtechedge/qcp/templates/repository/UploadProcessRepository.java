package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadProcessRepository extends JpaRepository<UploadProcess, String>, JpaSpecificationExecutor<UploadProcess> {

    boolean existsByProcessNameIgnoreCase(String processName);

    boolean existsByProcessNameIgnoreCaseAndProcessIdNot(String processName, String processId);
}
