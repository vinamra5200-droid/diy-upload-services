package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, String> {

    List<UploadJob> findByMakerUserIdOrderByCreatedAtDesc(String makerUserId);
}
