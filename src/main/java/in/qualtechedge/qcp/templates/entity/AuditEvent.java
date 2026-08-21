package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Append-only admin activity log ({@code audit_events}). Never updated or deleted — every
 * mutating service method appends a row via {@link in.qualtechedge.qcp.templates.service.AuditEventService}
 * (admin-api-contract.md §12.6).
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_code", nullable = false)
    private String eventCode;

    @CreationTimestamp
    @Column(name = "occurred_at", updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "process_id")
    private String processId;

    @Column(name = "template_code")
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutcome outcome;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;
}
