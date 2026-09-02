package in.qualtechedge.qcp.templates.utils;

import java.util.Map;

/**
 * Naming/config convention for the dead-letter topic {@link
 * in.qualtechedge.qcp.templates.service.impl.QueueConfigServiceImpl#accept} provisions alongside
 * every queue config's primary topic. Deterministic ({@code <topicName>.DLT}) rather than a
 * persisted field — consumer-callback-service computes the same name independently from the same
 * convention (see its own copy of this class), the same way the two services already agree on
 * topic names like {@code queue-config-topic} without sharing a field for it. {@code .DLT} matches
 * Spring Kafka's own {@code DeadLetterPublishingRecoverer} default suffix.
 * <p>
 * Config is fixed platform-wide, not derived from the primary topic's own settings: always {@code
 * delete} cleanup (a DLT must never compact away distinct failures sharing a key) and a 30-day
 * retention independent of the primary topic's own (possibly much shorter) retention, since DLT
 * messages need an operator-scale window to be noticed and replayed.
 */
public final class DltTopics {

    private static final String SUFFIX = ".DLT";
    private static final long RETENTION_MS = 30L * 24 * 3_600_000L;

    private DltTopics() {
    }

    public static String forSourceTopic(String topicName) {
        return topicName + SUFFIX;
    }

    public static Map<String, String> topicConfigs() {
        return Map.of("cleanup.policy", "delete", "retention.ms", String.valueOf(RETENTION_MS));
    }
}
