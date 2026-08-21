package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Upload process definition ({@code processes}). Maker drafts; Checker activates
 * (admin-api-contract.md §1). Named {@code UploadProcess}, not {@code Process}, to avoid
 * shadowing {@code java.lang.Process}.
 */
@Entity
@Table(name = "processes")
@Getter
@Setter
@NoArgsConstructor
public class UploadProcess {

    @Id
    @Column(name = "process_id")
    private String processId;

    @Column(name = "process_name", nullable = false)
    private String processName;

    @Column(nullable = false, columnDefinition = "text")
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigStatus status = ConfigStatus.draft;

    @Column(name = "validations_enabled", nullable = false)
    private boolean validationsEnabled = true;

    @Column(name = "validations_skip_reason", columnDefinition = "text")
    private String validationsSkipReason;

    @Column(name = "config_locked", nullable = false)
    private boolean configLocked = false;

    @Column(name = "config_lock_ref")
    private String configLockRef;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
