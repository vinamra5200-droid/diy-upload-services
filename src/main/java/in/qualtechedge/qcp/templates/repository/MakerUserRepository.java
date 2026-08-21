package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.MakerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MakerUserRepository extends JpaRepository<MakerUser, String> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndUserIdNot(String username, String userId);
}
