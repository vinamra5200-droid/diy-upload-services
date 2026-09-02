package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.QueueConfigOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueueConfigOutboxRepository extends JpaRepository<QueueConfigOutbox, Long> {

    /** Oldest-first, capped per poll so one tenant with a large backlog can't starve the others. */
    List<QueueConfigOutbox> findTop200ByOrderByOutboxIdAsc();
}
