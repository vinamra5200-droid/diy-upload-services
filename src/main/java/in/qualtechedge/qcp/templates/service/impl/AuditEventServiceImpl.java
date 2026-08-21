package in.qualtechedge.qcp.templates.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.response.AuditEventResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.entity.AuditEvent;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.repository.AuditEventRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventServiceImpl implements AuditEventService {

    private static final String CSV_HEADER =
            "eventId,eventCode,occurredAt,actorId,actorRoles,processId,templateCode,templateVersion,"
            + "outcome,summary,traceId,uploadAttemptId,submissionId,jobId,payload,prevEventId\n";

    private final AuditEventRepository auditEventRepository;

    @Override
    @Transactional
    public void record(String eventCode, String actorId, String processId, String templateCode,
                        AuditOutcome outcome, String summary) {
        log.debug("Recording audit event: eventCode={}, actorId={}", eventCode, actorId);
        AuditEvent event = new AuditEvent();
        event.setEventId(IdGenerator.generate("evt"));
        event.setEventCode(eventCode);
        event.setActorId(actorId);
        event.setProcessId(processId);
        event.setTemplateCode(templateCode);
        event.setOutcome(outcome);
        event.setSummary(summary);
        auditEventRepository.save(event);
    }

    @Override
    @Transactional
    public void record(PipelineAuditEventRequest request) {
        log.debug("Recording pipeline audit event: eventCode={}, actorId={}, uploadAttemptId={}",
                request.eventCode(), request.actorId(), request.uploadAttemptId());
        String prevEventId = request.uploadAttemptId() == null ? null
                : auditEventRepository.findTopByUploadAttemptIdOrderByOccurredAtDesc(request.uploadAttemptId())
                        .map(AuditEvent::getEventId)
                        .orElse(null);

        AuditEvent event = new AuditEvent();
        event.setEventId(IdGenerator.generate("evt"));
        event.setEventCode(request.eventCode().name());
        event.setActorId(request.actorId());
        event.setActorRoles(JsonColumnMapper.write(request.actorRoles()));
        event.setProcessId(request.processId());
        event.setTemplateCode(request.templateCode());
        event.setTemplateVersion(request.templateVersion());
        event.setOutcome(request.outcome());
        event.setSummary(request.summary());
        event.setTraceId(request.traceId());
        event.setUploadAttemptId(request.uploadAttemptId());
        event.setSubmissionId(request.submissionId());
        event.setJobId(request.jobId());
        event.setPayload(JsonColumnMapper.write(request.payload()));
        event.setPrevEventId(prevEventId);
        auditEventRepository.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(String processId, String actorId, String eventCode, AuditOutcome outcome,
                                                 OffsetDateTime from, OffsetDateTime to, int page, int limit) {
        log.debug("Listing audit events: processId={}, actorId={}, eventCode={}, outcome={}", processId, actorId, eventCode, outcome);
        Specification<AuditEvent> spec = buildSpecification(processId, actorId, eventCode, outcome, from, to);
        int size = Math.min(Math.max(limit, 1), 200);
        int zeroBasedPage = Math.max(page - 1, 0);
        Page<AuditEvent> result = auditEventRepository.findAll(spec,
                PageRequest.of(zeroBasedPage, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(String processId, String actorId, String eventCode, AuditOutcome outcome,
                             OffsetDateTime from, OffsetDateTime to) {
        log.debug("Exporting audit events: processId={}, actorId={}, eventCode={}, outcome={}", processId, actorId, eventCode, outcome);
        Specification<AuditEvent> spec = buildSpecification(processId, actorId, eventCode, outcome, from, to);
        List<AuditEvent> events = auditEventRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "occurredAt"));
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (AuditEvent event : events) {
            csv.append(csvField(event.getEventId())).append(',')
                    .append(csvField(event.getEventCode())).append(',')
                    .append(csvField(event.getOccurredAt() == null ? "" : event.getOccurredAt().toString())).append(',')
                    .append(csvField(event.getActorId())).append(',')
                    .append(csvField(event.getActorRoles())).append(',')
                    .append(csvField(event.getProcessId())).append(',')
                    .append(csvField(event.getTemplateCode())).append(',')
                    .append(csvField(event.getTemplateVersion())).append(',')
                    .append(csvField(event.getOutcome() == null ? "" : event.getOutcome().name())).append(',')
                    .append(csvField(event.getSummary())).append(',')
                    .append(csvField(event.getTraceId())).append(',')
                    .append(csvField(event.getUploadAttemptId())).append(',')
                    .append(csvField(event.getSubmissionId())).append(',')
                    .append(csvField(event.getJobId())).append(',')
                    .append(csvField(event.getPayload())).append(',')
                    .append(csvField(event.getPrevEventId())).append('\n');
        }
        return csv.toString();
    }

    private Specification<AuditEvent> buildSpecification(String processId, String actorId, String eventCode,
                                                          AuditOutcome outcome, OffsetDateTime from, OffsetDateTime to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (processId != null && !processId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("processId"), processId));
            }
            if (actorId != null && !actorId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("actorId"), actorId));
            }
            if (eventCode != null && !eventCode.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("eventCode"), eventCode));
            }
            if (outcome != null) {
                predicates.add(criteriaBuilder.equal(root.get("outcome"), outcome));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() { };

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getEventId(), event.getEventCode(), event.getOccurredAt(),
                event.getActorId(), JsonColumnMapper.read(event.getActorRoles(), STRING_LIST),
                event.getProcessId(), event.getTemplateCode(), event.getTemplateVersion(),
                event.getOutcome(), event.getSummary(),
                event.getTraceId(), event.getUploadAttemptId(), event.getSubmissionId(), event.getJobId(),
                JsonColumnMapper.read(event.getPayload(), STRING_OBJECT_MAP), event.getPrevEventId());
    }
}
