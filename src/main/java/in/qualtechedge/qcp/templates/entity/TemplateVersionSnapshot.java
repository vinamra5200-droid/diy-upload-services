package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Append-only, one row per save: a full point-in-time copy of a template
 * ({@code template_version_snapshots}) (admin-api-contract.md §2.8/§2.9). Never updated or
 * deleted.
 */
@Entity
@Table(name = "template_version_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class TemplateVersionSnapshot {

    @Id
    @Column(name = "snapshot_id")
    private String snapshotId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "captured_by", nullable = false)
    private String capturedBy;

    @CreationTimestamp
    @Column(name = "captured_at", updatable = false)
    private OffsetDateTime capturedAt;
}
