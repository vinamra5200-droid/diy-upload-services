package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.ConfigLock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigLockRepository extends JpaRepository<ConfigLock, String> {

    boolean existsByProcessId(String processId);

    List<ConfigLock> findByLockedAtBefore(OffsetDateTime cutoff);
}
