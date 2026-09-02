package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueueConfigRepository extends JpaRepository<QueueConfig, String> {

    boolean existsByQueueConfigNameIgnoreCase(String queueConfigName);

    boolean existsByQueueConfigNameIgnoreCaseAndQueueConfigIdNot(String queueConfigName, String queueConfigId);

    boolean existsByTopicNameIgnoreCase(String topicName);

    boolean existsByTopicNameIgnoreCaseAndQueueConfigIdNot(String topicName, String queueConfigId);

    /**
     * Every active queue config bound to one API config — used by
     * {@link in.qualtechedge.qcp.templates.service.impl.ApiConfigServiceImpl#update} to re-publish
     * a {@code queue-config-topic} event for each one when the API config they're bound to changes,
     * since that change is embedded in the event payload (see {@code QueueConfigEvent.apiConfig}).
     */
    List<QueueConfig> findAllByApiConfigIdAndStatus(String apiConfigId, ConfigStatus status);
}
