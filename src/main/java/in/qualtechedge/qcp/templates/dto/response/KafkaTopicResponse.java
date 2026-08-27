package in.qualtechedge.qcp.templates.dto.response;

public record KafkaTopicResponse(
        String name,
        int partitions,
        int replicationFactor
) {
}
