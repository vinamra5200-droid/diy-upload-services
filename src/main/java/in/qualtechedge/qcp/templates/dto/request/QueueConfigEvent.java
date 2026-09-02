package in.qualtechedge.qcp.templates.dto.request;

import java.time.OffsetDateTime;

/**
 * Published to {@code queue-config-topic} (compacted, keyed {@code {tenantCode}:{queueConfigId}})
 * by {@link in.qualtechedge.qcp.templates.service.impl.QueueConfigEventPublisherImpl} whenever an
 * {@code active} {@code QueueConfig} — or the {@code ApiConfig} it's bound to — changes. Lets
 * consumer-callback-service maintain a local {@code (tenantCode, topicName) -> ApiConfig} cache
 * instead of pulling this over REST at save time (see docs/consumer-callback-service-plan.md §3);
 * only {@code active} configs are ever published — a draft/rejected/waitingForChecker row is not
 * consumable, so there's nothing for a subscriber to act on.
 * <p>
 * {@code apiConfig} carries only the fields a caller needs to build the outbound HTTP request —
 * deliberately never {@code auth}. See docs/consumer-callback-service-plan.md §5 for how the auth
 * secret should be resolved on the consuming side instead of being copied here.
 */
public record QueueConfigEvent(
        String queueConfigId,
        String tenantCode,
        String topicName,
        int consumerConcurrency,
        String status,
        OffsetDateTime updatedAt,
        ApiConfigSnapshot apiConfig
) {
    public record ApiConfigSnapshot(
            String apiConfigId,
            String method,
            String uri,
            String queryParams,
            String headers,
            String body
    ) {
    }
}
