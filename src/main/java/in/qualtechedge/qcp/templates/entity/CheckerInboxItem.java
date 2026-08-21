package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ChangeEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping onto the {@code v_checker_inbox} view (admin-api-contract.md §8) — pending
 * Maker submissions across every governed entity.
 */
@Entity
@Immutable
@Table(name = "v_checker_inbox")
@Getter
@NoArgsConstructor
public class CheckerInboxItem {

    @Id
    @Column(name = "change_id")
    private String changeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ChangeEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "entity_label", nullable = false)
    private String entityLabel;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "actor_ne_submitter", nullable = false)
    private boolean actorNeSubmitter;

    @Column(name = "process_id_ref")
    private String processIdRef;
}
