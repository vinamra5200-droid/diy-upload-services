package in.qualtechedge.qcp.templates.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KafkaTopicRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @Min(value = 1, message = "partitions must be at least 1")
        int partitions,

        @Min(value = 1, message = "replicationFactor must be at least 1")
        int replicationFactor
) {
}
