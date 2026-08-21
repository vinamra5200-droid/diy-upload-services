package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Database connection ({@code database_connections}) — standalone admin resource and the target
 * for a template's Post-Load Action when {@code databaseMode = "useExisting"}
 * (admin-api-contract.md §6). {@code tableNames} is the child {@code database_connection_tables}
 * table, mapped as an ordered value collection.
 */
@Entity
@Table(name = "database_connections")
@Getter
@Setter
@NoArgsConstructor
public class DatabaseConnection {

    @Id
    @Column(name = "connection_id")
    private String connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatabaseProvider provider;

    @Column(name = "connection_label", nullable = false)
    private String connectionLabel;

    @Column(name = "connection_ref", nullable = false)
    private String connectionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigStatus status = ConfigStatus.draft;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "database_connection_tables", joinColumns = @JoinColumn(name = "connection_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "table_name")
    private List<String> tableNames = new ArrayList<>();
}
