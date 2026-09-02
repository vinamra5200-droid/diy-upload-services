package in.qualtechedge.qcp.templates.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.qualtechedge.qcp.templates.entity.QueueConfig;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.TopicCleanupPolicy;
import in.qualtechedge.qcp.templates.mapper.QueueConfigMapper;
import in.qualtechedge.qcp.templates.repository.ApiConfigRepository;
import in.qualtechedge.qcp.templates.repository.QueueConfigRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.KafkaTopicAdminService;
import in.qualtechedge.qcp.templates.service.QueueConfigEventPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class QueueConfigServiceImplTest {

    @Mock
    private QueueConfigRepository queueConfigRepository;

    @Mock
    private ApiConfigRepository apiConfigRepository;

    @Mock
    private KafkaTopicAdminService kafkaTopicAdminService;

    @Mock
    private QueueConfigEventPublisher queueConfigEventPublisher;

    @Mock
    private AuditEventService auditEventService;

    private QueueConfigServiceImpl queueConfigService;

    @BeforeEach
    void setUp() {
        queueConfigService = new QueueConfigServiceImpl(queueConfigRepository, apiConfigRepository,
                new QueueConfigMapper(), kafkaTopicAdminService, queueConfigEventPublisher, auditEventService);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("actorId", "checker_admin_01")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accept_createsPrimaryTopicAndDltTopicOnBroker() {
        QueueConfig entity = new QueueConfig();
        entity.setQueueConfigId("queue-a1b2c3d4");
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy("maker_admin_01");
        entity.setTopicName("orders-topic");
        entity.setTopicPartitions(3);
        entity.setTopicReplicationFactor(1);
        entity.setTopicRetentionHours(168);
        entity.setTopicCleanupPolicy(TopicCleanupPolicy.delete);
        when(queueConfigRepository.findById("queue-a1b2c3d4")).thenReturn(Optional.of(entity));
        when(queueConfigRepository.save(any(QueueConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        queueConfigService.accept("queue-a1b2c3d4");

        verify(kafkaTopicAdminService).createTopic(eq("orders-topic"), eq(3), eq(1), any(Map.class));
        verify(kafkaTopicAdminService).createTopic(eq("orders-topic.DLT"), eq(3), eq(1),
                eq(Map.of("cleanup.policy", "delete", "retention.ms", String.valueOf(30L * 24 * 3_600_000L))));
    }
}
