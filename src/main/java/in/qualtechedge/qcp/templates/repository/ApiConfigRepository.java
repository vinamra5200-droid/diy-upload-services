package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.ApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiConfigRepository extends JpaRepository<ApiConfig, String> {

    boolean existsByLabelIgnoreCase(String label);

    boolean existsByLabelIgnoreCaseAndConfigIdNot(String label, String configId);
}
