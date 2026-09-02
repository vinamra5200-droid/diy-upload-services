package in.qualtechedge.qcp.templates.scheduler;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigEvent;
import in.qualtechedge.qcp.templates.entity.QueueConfigOutbox;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.multitenancy.registry.Tenant;
import in.qualtechedge.qcp.templates.multitenancy.registry.TenantRepository;
import in.qualtechedge.qcp.templates.properties.KafkaBatchProperties;
import in.qualtechedge.qcp.templates.repository.QueueConfigOutboxRepository;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains {@code queue_config_outbox} to {@code queue-config-topic} — the other half of the
 * transactional outbox {@link in.qualtechedge.qcp.templates.service.impl.QueueConfigEventPublisherImpl}
 * writes into. Always publishes on the one shared Kafka cluster (the injected default
 * {@link KafkaTemplate}) — this is control-plane metadata about queue configs, not the tenant data
 * those queue configs' own topics carry.
 * <p>
 * {@code queue_config_outbox} is a per-tenant table, and this runs with no request/tenant context
 * (it's a scheduled job, not a request), so it visits every active tenant explicitly — same
 * async-boundary rule {@link HostContext}'s own doc comment calls out, and the same shape as
 * {@link ConfigLockReaper}/{@link UploadPipelineReaper}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueConfigOutboxPublisher {

    private static final int SEND_TIMEOUT_SECONDS = 10;

    private final TenantRepository tenantRepository;
    private final QueueConfigOutboxRepository queueConfigOutboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final KafkaBatchProperties kafkaBatchProperties;

    @Scheduled(fixedDelayString = "${qcp.kafka.queue-config-outbox-poll-interval-ms:5000}")
    public void publishPending() {
        for (Tenant tenant : tenantRepository.findAllByStatus(Tenant.STATUS_ACTIVE)) {
            HostContext.setCurrentTenant(tenant.getShortCode());
            try {
                publishPendingForCurrentTenant();
            } finally {
                HostContext.clear();
            }
        }
    }

    @Transactional
    void publishPendingForCurrentTenant() {
        List<QueueConfigOutbox> pending = queueConfigOutboxRepository.findTop200ByOrderByOutboxIdAsc();
        for (QueueConfigOutbox row : pending) {
            if (!publishOne(row)) {
                // Kept in arrival order (findTop200ByOrderByOutboxIdAsc) — stop at the first
                // failure for this tenant rather than publishing later rows out of order while an
                // earlier one is still stuck, and retry the whole remaining backlog next poll.
                return;
            }
        }
    }

    private boolean publishOne(QueueConfigOutbox row) {
        QueueConfigEvent event = JsonColumnMapper.read(row.getPayload(), QueueConfigEvent.class);
        try {
            kafkaTemplate.send(kafkaBatchProperties.getTopics().getQueueConfig(), row.getEventKey(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted publishing queue-config-topic event: outboxId={}", row.getOutboxId(), e);
            return false;
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to publish queue-config-topic event, left for next poll: outboxId={}", row.getOutboxId(), e);
            return false;
        }
        queueConfigOutboxRepository.delete(row);
        return true;
    }
}
