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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only audit log ({@code audit_events}) — every {@code event_code} it may contain is
 * catalogued in the static {@code audit_event_catalogue} table (V1_0_59), enforced by a foreign
 * key. Never updated or deleted. Two categories of caller:
 * <ul>
 *   <li>Admin config mutations (admin-api-contract.md §9/§12.6) — every resource's maker-checker
 *       service methods, via {@link in.qualtechedge.qcp.templates.service.AuditEventService#record(String, String, String, String, AuditOutcome, String)}.</li>
 *   <li>Upload-pipeline events (Solution Design §12) — via
 *       {@link in.qualtechedge.qcp.templates.service.AuditEventService#record(in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest)},
 *       which also fills {@code traceId}/{@code uploadAttemptId}/{@code submissionId}/
 *       {@code jobId}/{@code actorRoles}/{@code templateVersion}/{@code payload}/{@code prevEventId}
 *       — all null for admin-mutation rows.</li>
 * </ul>
 * {@code actorRoles}/{@code payload} are JSONB columns kept as raw JSON text here (as elsewhere in
 * this codebase — see {@link in.qualtechedge.qcp.templates.entity.ApiConfig}), converted to/from
 * typed values in {@link in.qualtechedge.qcp.templates.service.impl.AuditEventServiceImpl} via
 * {@link in.qualtechedge.qcp.templates.utils.JsonColumnMapper}.
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

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "upload_attempt_id")
    private String uploadAttemptId;

    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "job_id")
    private String jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actor_roles", columnDefinition = "jsonb")
    private String actorRoles;

    @Column(name = "template_version")
    private String templateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "prev_event_id")
    private String prevEventId;
}
