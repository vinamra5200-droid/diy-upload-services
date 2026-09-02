package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One pending {@code queue-config-topic} event ({@code queue_config_outbox}, V1_4_14) — written in
 * the same transaction as the {@code queue_configs}/{@code api_configs} change it describes
 * ({@link in.qualtechedge.qcp.templates.service.impl.QueueConfigEventPublisherImpl}), so the event
 * can never be lost between "saved to Postgres" and "sent to Kafka". Deleted by
 * {@link in.qualtechedge.qcp.templates.scheduler.QueueConfigOutboxPublisher} once its send is
 * acknowledged — this table only ever holds the backlog still waiting to go out.
 */
@Entity
@Table(name = "queue_config_outbox")
@Getter
@Setter
@NoArgsConstructor
public class QueueConfigOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "queue_config_id", nullable = false)
    private String queueConfigId;

    /** {@code {tenantCode}:{queueConfigId}} — the Kafka message key. */
    @Column(name = "event_key", nullable = false)
    private String eventKey;

    /** Serialized {@link in.qualtechedge.qcp.templates.dto.request.QueueConfigEvent}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
