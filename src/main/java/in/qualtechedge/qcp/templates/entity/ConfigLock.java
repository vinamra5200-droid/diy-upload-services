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

/**
 * One row per in-flight upload holding a process's config lock ({@code config_locks}) — see
 * {@link in.qualtechedge.qcp.templates.service.ConfigLockService}. A process counts as locked
 * against maker-admin config edits whenever any row exists for it; {@code lockRef} is the
 * uploadId (later reassigned to the Kafka batchId), globally unique, hence the primary key.
 */
@Entity
@Table(name = "config_locks")
@Getter
@Setter
@NoArgsConstructor
public class ConfigLock {

    @Id
    @Column(name = "lock_ref")
    private String lockRef;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @CreationTimestamp
    @Column(name = "locked_at", updatable = false)
    private OffsetDateTime lockedAt;
}
