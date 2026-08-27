package in.qualtechedge.qcp.templates.entity;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.DatabaseActionMode;
import in.qualtechedge.qcp.templates.enums.DatabaseProvider;
import in.qualtechedge.qcp.templates.enums.DuplicateRowAction;
import in.qualtechedge.qcp.templates.enums.KafkaMode;
import in.qualtechedge.qcp.templates.enums.PostLoadActionType;
import in.qualtechedge.qcp.templates.enums.RowOrderMode;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Upload template parent row ({@code templates}) (admin-api-contract.md §2). Child collections
 * (fields, upload formats, pk/sort fields, checker roles, transformations, validation rules,
 * version snapshots) are NOT mapped as JPA relationships here — each is a standalone entity with
 * a plain {@code template_id} column, composed by
 * {@link in.qualtechedge.qcp.templates.service.impl.TemplateServiceImpl} via its own repository.
 * That keeps each child's "full replace on update" semantics explicit (delete-by-templateId +
 * insert) instead of relying on cascade/orphanRemoval timing across seven collections.
 */
@Entity
@Table(name = "templates")
@Getter
@Setter
@NoArgsConstructor
public class Template {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "template_description", nullable = false, columnDefinition = "text")
    private String templateDescription = "";

    @Column(nullable = false)
    private String version = "1.0.0";

    @Column(name = "process_id", nullable = false)
    private String processId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigStatus status = ConfigStatus.draft;

    @Column(name = "package_max_size_mb", nullable = false)
    private int packageMaxSizeMb = 50;

    @Column(name = "package_max_rows")
    private Integer packageMaxRows;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_action", nullable = false)
    private DuplicateRowAction duplicateAction = DuplicateRowAction.reject;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_order", nullable = false)
    private RowOrderMode rowOrder = RowOrderMode.inputSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_load_action_type", nullable = false)
    private PostLoadActionType postLoadActionType = PostLoadActionType.kafka;

    @Column(name = "kafka_topic")
    private String kafkaTopic;

    // Per-template Kafka cluster override for the "custom" kafkaMode path — blank means the
    // shared spring.kafka.bootstrap-servers cluster. See PostLoadActionDispatcherImpl#resolveTarget
    // and KafkaProducerRegistry, which open (and cache) a producer against this cluster on demand.
    @Column(name = "kafka_bootstrap_servers")
    private String kafkaBootstrapServers;

    // V1_4_0 — when set to useExisting, kafkaQueueConfigId names the queue_configs row supplying
    // the real topic (see PostLoadActionDispatcherImpl); kafkaTopic/kafkaBootstrapServers above are
    // then ignored. custom/null keeps the direct kafkaTopic/kafkaBootstrapServers behavior.
    @Enumerated(EnumType.STRING)
    @Column(name = "kafka_mode")
    private KafkaMode kafkaMode;

    @Column(name = "kafka_queue_config_id")
    private String kafkaQueueConfigId;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_mode")
    private DatabaseActionMode databaseMode;

    @Column(name = "database_connection_id")
    private String databaseConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_provider")
    private DatabaseProvider databaseProvider;

    @Column(name = "database_connection_ref")
    private String databaseConnectionRef;

    @Column(name = "database_table_name")
    private String databaseTableName;

    @Column(name = "upload_process_timeout_minutes", nullable = false)
    private int uploadProcessTimeoutMinutes = 10;

    @Column(name = "validation_worker_threads", nullable = false)
    private int validationWorkerThreads = 10;

    @Column(name = "validations_enabled", nullable = false)
    private boolean validationsEnabled = true;

    @Column(name = "maker_checker_enabled", nullable = false)
    private boolean makerCheckerEnabled = false;

    @Column(name = "maker_checker_actor_ne_submitter", nullable = false)
    private boolean makerCheckerActorNeSubmitter = true;

    @Column(name = "maker_checker_sla_hours", nullable = false)
    private int makerCheckerSlaHours = 24;

    @Column(name = "maker_checker_escalate_to_role", nullable = false)
    private String makerCheckerEscalateToRole = "";

    @Column(name = "fail_fast", nullable = false)
    private boolean failFast = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String schedule;

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
