package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.ExampleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExampleEntityRepository extends JpaRepository<ExampleEntity, UUID> {
}
