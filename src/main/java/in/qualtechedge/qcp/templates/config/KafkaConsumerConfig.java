package in.qualtechedge.qcp.templates.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Cross-cutting config for {@link in.qualtechedge.qcp.templates.consumer.BatchValidationCompletedListener}
 * — the only inbound Kafka consumer this repo has (everything else is a producer). Same
 * retry-then-dead-letter shape as validation-service's own {@code config.KafkaConfig}, so a poison
 * completion message never blocks the partition.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
    }
}
