package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.response.AuditEventResponse;
import in.qualtechedge.qcp.templates.dto.response.PageResponse;
import in.qualtechedge.qcp.templates.entity.AuditEvent;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.repository.AuditEventRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private static final String CSV_HEADER = "eventId,eventCode,occurredAt,actorId,processId,templateCode,outcome,summary\n";

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
                    .append(csvField(event.getProcessId())).append(',')
                    .append(csvField(event.getTemplateCode())).append(',')
                    .append(csvField(event.getOutcome() == null ? "" : event.getOutcome().name())).append(',')
                    .append(csvField(event.getSummary())).append('\n');
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

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getEventId(), event.getEventCode(), event.getOccurredAt(),
                event.getActorId(), event.getProcessId(), event.getTemplateCode(), event.getOutcome(), event.getSummary());
    }
}
