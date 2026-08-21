package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, String> {

    boolean existsByConnectionLabelIgnoreCase(String connectionLabel);

    boolean existsByConnectionLabelIgnoreCaseAndConnectionIdNot(String connectionLabel, String connectionId);
}
