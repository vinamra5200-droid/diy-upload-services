package in.qualtechedge.qcp.templates.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.qualtechedge.qcp.templates.dto.request.ProcessRequest;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.ProcessMapper;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import java.time.Instant;
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
class ProcessServiceImplTest {

    @Mock
    private UploadProcessRepository uploadProcessRepository;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private ConfigLockService configLockService;

    private ProcessServiceImpl processService;

    @BeforeEach
    void setUp() {
        processService = new ProcessServiceImpl(uploadProcessRepository, new ProcessMapper(), auditEventService, configLockService);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("actorId", "maker_admin_01")
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
    void create_savesEntityAndReturnsResponse() {
        ProcessRequest request = new ProcessRequest("Vendor Onboarding", "Bulk vendor creation");
        when(uploadProcessRepository.existsByProcessNameIgnoreCase("Vendor Onboarding")).thenReturn(false);
        when(uploadProcessRepository.saveAndFlush(any(UploadProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessResponse response = processService.create(request);

        assertThat(response.processId()).startsWith("proc-");
        assertThat(response.processName()).isEqualTo("Vendor Onboarding");
        assertThat(response.status()).isEqualTo(ConfigStatus.draft);
        assertThat(response.createdBy()).isEqualTo("maker_admin_01");
        verify(auditEventService).record(eq("ADMIN_PROCESS_CREATED"), eq("maker_admin_01"), anyString(), isNull(),
                eq(AuditOutcome.SUCCESS), anyString());
    }

    @Test
    void create_whenNameTaken_throwsConflictException() {
        when(uploadProcessRepository.existsByProcessNameIgnoreCase("Vendor Onboarding")).thenReturn(true);

        assertThatThrownBy(() -> processService.create(new ProcessRequest("Vendor Onboarding", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsResourceNotFoundException() {
        when(uploadProcessRepository.findById("proc-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processService.getById("proc-missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("proc-missing");
    }

    @Test
    void submit_whenActive_throwsConflictException() {
        UploadProcess entity = new UploadProcess();
        entity.setProcessId("proc-a1b2c3d4");
        entity.setStatus(ConfigStatus.active);
        when(uploadProcessRepository.findById("proc-a1b2c3d4")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> processService.submit("proc-a1b2c3d4"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void accept_whenSubmitterIsActor_throwsConflictException() {
        UploadProcess entity = new UploadProcess();
        entity.setProcessId("proc-a1b2c3d4");
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy("maker_admin_01");
        when(uploadProcessRepository.findById("proc-a1b2c3d4")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> processService.accept("proc-a1b2c3d4"))
                .isInstanceOf(ConflictException.class);
    }
}
