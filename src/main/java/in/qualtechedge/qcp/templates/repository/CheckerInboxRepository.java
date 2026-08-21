package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.CheckerInboxItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckerInboxRepository extends JpaRepository<CheckerInboxItem, String> {

    /** Four-eyes at the list level too: hide items this actor submitted themselves. */
    List<CheckerInboxItem> findBySubmittedByNot(String actorId);
}
