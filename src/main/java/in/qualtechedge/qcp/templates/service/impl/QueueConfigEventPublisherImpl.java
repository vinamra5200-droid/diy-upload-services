package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.QueueConfigEvent;
import in.qualtechedge.qcp.templates.entity.ApiConfig;
import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.entity.QueueConfigOutbox;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.ApiConfigRepository;
import in.qualtechedge.qcp.templates.repository.QueueConfigOutboxRepository;
import in.qualtechedge.qcp.templates.service.QueueConfigEventPublisher;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueConfigEventPublisherImpl implements QueueConfigEventPublisher {

    private final QueueConfigOutboxRepository queueConfigOutboxRepository;
    private final ApiConfigRepository apiConfigRepository;

    @Override
    @Transactional
    public void publish(QueueConfig queueConfig) {
        if (queueConfig.getStatus() != ConfigStatus.active) {
            return;
        }
        String tenantCode = HostContext.getCurrentTenant();
        QueueConfigEvent.ApiConfigSnapshot apiConfigSnapshot = queueConfig.getApiConfigId() == null ? null
                : apiConfigRepository.findById(queueConfig.getApiConfigId()).map(this::toSnapshot).orElse(null);

        QueueConfigEvent event = new QueueConfigEvent(
                queueConfig.getQueueConfigId(), tenantCode, queueConfig.getTopicName(),
                queueConfig.getTopicConsumerConcurrency(),
                queueConfig.getStatus().name(), queueConfig.getUpdatedAt(), apiConfigSnapshot);

        QueueConfigOutbox outbox = new QueueConfigOutbox();
        outbox.setQueueConfigId(queueConfig.getQueueConfigId());
        outbox.setEventKey(tenantCode + ":" + queueConfig.getQueueConfigId());
        outbox.setPayload(JsonColumnMapper.write(event));
        queueConfigOutboxRepository.save(outbox);
        log.debug("Queued queue-config-topic event: queueConfigId={}, tenantCode={}",
                queueConfig.getQueueConfigId(), tenantCode);
    }

    /** Deliberately omits {@link ApiConfig#getAuth()} — see {@link QueueConfigEvent}'s javadoc. */
    private QueueConfigEvent.ApiConfigSnapshot toSnapshot(ApiConfig apiConfig) {
        return new QueueConfigEvent.ApiConfigSnapshot(apiConfig.getConfigId(), apiConfig.getMethod().name(),
                apiConfig.getUri(), apiConfig.getQueryParams(), apiConfig.getHeaders(), apiConfig.getBody());
    }
}
