package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Upload role gating maker-user access to processes ({@code upload_roles})
 * (admin-api-contract.md §3). {@code processAccess} is the child {@code upload_role_processes}
 * join table — a plain set (the source schema carries no ordering column for it).
 */
@Entity
@Table(name = "upload_roles")
@Getter
@Setter
@NoArgsConstructor
public class UploadRole {

    @Id
    @Column(name = "role_id")
    private String roleId;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(nullable = false, columnDefinition = "text")
    private String description = "";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigStatus status = ConfigStatus.draft;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "upload_role_processes", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "process_id")
    private Set<String> processAccess = new LinkedHashSet<>();
}
