package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.QueueConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueueConfigRepository extends JpaRepository<QueueConfig, String> {

    boolean existsByQueueConfigNameIgnoreCase(String queueConfigName);

    boolean existsByQueueConfigNameIgnoreCaseAndQueueConfigIdNot(String queueConfigName, String queueConfigId);

    boolean existsByTopicNameIgnoreCase(String topicName);

    boolean existsByTopicNameIgnoreCaseAndQueueConfigIdNot(String topicName, String queueConfigId);
}
