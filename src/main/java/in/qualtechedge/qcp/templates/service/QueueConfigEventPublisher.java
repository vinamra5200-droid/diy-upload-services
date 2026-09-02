package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.entity.QueueConfig;

/**
 * Writes a {@code queue-config-topic} event into the transactional outbox
 * ({@code queue_config_outbox}) for one {@code active} queue config — see
 * {@link in.qualtechedge.qcp.templates.entity.QueueConfigOutbox}'s javadoc for why this is a table
 * write, not a direct Kafka send, and {@link in.qualtechedge.qcp.templates.dto.request.QueueConfigEvent}
 * for the payload shape. Callers (create/update/accept flows) always run this inside their own
 * {@code @Transactional} method, so the outbox row commits atomically with the entity change it
 * describes.
 */
public interface QueueConfigEventPublisher {

    /**
     * No-ops for anything not currently {@code active} — a draft/rejected/waitingForChecker queue
     * config isn't consumable, so publishing one would just be noise for every subscriber's cache.
     */
    void publish(QueueConfig queueConfig);
}
