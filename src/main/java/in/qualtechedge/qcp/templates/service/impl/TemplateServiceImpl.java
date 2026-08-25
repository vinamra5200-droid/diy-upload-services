package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.CloneTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.CreateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UpdateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateVersionSnapshotResponse;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.TemplateCheckerRole;
import in.qualtechedge.qcp.templates.entity.TemplateField;
import in.qualtechedge.qcp.templates.entity.TemplatePkField;
import in.qualtechedge.qcp.templates.entity.TemplateSortField;
import in.qualtechedge.qcp.templates.entity.TemplateTransformation;
import in.qualtechedge.qcp.templates.entity.TemplateUploadFormat;
import in.qualtechedge.qcp.templates.entity.TemplateValidationRule;
import in.qualtechedge.qcp.templates.entity.TemplateVersionSnapshot;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.ValidationRuleType;
import in.qualtechedge.qcp.templates.exception.ConfigLockedException;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.TemplateMapper;
import in.qualtechedge.qcp.templates.repository.TemplateCheckerRoleRepository;
import in.qualtechedge.qcp.templates.repository.TemplateFieldRepository;
import in.qualtechedge.qcp.templates.repository.TemplatePkFieldRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.TemplateSortFieldRepository;
import in.qualtechedge.qcp.templates.repository.TemplateTransformationRepository;
import in.qualtechedge.qcp.templates.repository.TemplateUploadFormatRepository;
import in.qualtechedge.qcp.templates.repository.TemplateValidationRuleRepository;
import in.qualtechedge.qcp.templates.repository.TemplateVersionSnapshotRepository;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.TemplateService;
import in.qualtechedge.qcp.templates.utils.ConfigLifecycleGuard;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final TemplateUploadFormatRepository templateUploadFormatRepository;
    private final TemplatePkFieldRepository templatePkFieldRepository;
    private final TemplateSortFieldRepository templateSortFieldRepository;
    private final TemplateCheckerRoleRepository templateCheckerRoleRepository;
    private final TemplateTransformationRepository templateTransformationRepository;
    private final TemplateValidationRuleRepository templateValidationRuleRepository;
    private final TemplateVersionSnapshotRepository templateVersionSnapshotRepository;
    private final UploadProcessRepository uploadProcessRepository;
    private final TemplateMapper templateMapper;
    private final AuditEventService auditEventService;
    private final ConfigLockService configLockService;

    @Override
    @Transactional(readOnly = true)
    public List<TemplateSummaryResponse> listByProcess(String processId, ConfigStatus status) {
        log.debug("Listing templates: processId={}, status={}", processId, status);
        Specification<Template> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("processId"), processId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec).stream()
                .map(template -> templateMapper.toSummaryResponse(template,
                        (int) templateFieldRepository.countByTemplateId(template.getTemplateId()),
                        (int) templateValidationRuleRepository.countByTemplateId(template.getTemplateId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getById(String templateId) {
        log.debug("Fetching template: id={}", templateId);
        return buildResponse(findOrThrow(templateId));
    }

    @Override
    @Transactional
    public TemplateResponse create(String processId, CreateTemplateRequest request) {
        log.debug("Creating template: processId={}, name={}", processId, request.templateName());
        assertProcessExistsAndNotLocked(processId);
        String actorId = CurrentActor.id();
        Template entity = templateMapper.toEntity(request, processId, actorId);
        entity.setTemplateId(nextTemplateId());
        Template saved = templateRepository.saveAndFlush(entity);
        // templates_seed_formats DB trigger already inserted the 3 default format rows.
        TemplateResponse response = buildResponse(saved);
        captureVersionSnapshotIfNew(saved, response, actorId);
        auditEventService.record("ADMIN_TEMPLATE_CREATED", actorId, processId, saved.getTemplateCode(),
                AuditOutcome.SUCCESS, "Template " + saved.getTemplateId() + " created");
        return response;
    }

    @Override
    @Transactional
    public TemplateResponse update(String templateId, UpdateTemplateRequest request) {
        log.debug("Updating template: id={}", templateId);
        Template entity = findOrThrow(templateId);
        assertProcessExistsAndNotLocked(entity.getProcessId());
        ConfigLifecycleGuard.assertEditable(entity.getStatus());
        assertAtLeastOneFormatEnabled(request);

        templateMapper.applyUpdate(entity, request);
        if (entity.getStatus() == ConfigStatus.active) {
            entity.setVersion(bumpPatchVersion(entity.getVersion()));
            entity.setStatus(ConfigStatus.draft);
        }
        Template saved = templateRepository.save(entity);
        replaceChildren(templateId, request);

        String actorId = CurrentActor.id();
        TemplateResponse response = buildResponse(saved);
        captureVersionSnapshotIfNew(saved, response, actorId);
        auditEventService.record("ADMIN_TEMPLATE_UPDATED", actorId, saved.getProcessId(), saved.getTemplateCode(),
                AuditOutcome.SUCCESS, "Template " + templateId + " updated");
        return response;
    }

    @Override
    @Transactional
    public TemplateResponse submit(String templateId) {
        log.debug("Submitting template: id={}", templateId);
        Template entity = findOrThrow(templateId);
        assertProcessExistsAndNotLocked(entity.getProcessId());
        ConfigLifecycleGuard.assertSubmittable(entity.getStatus());
        assertAtLeastOneFormatEnabledPersisted(templateId);
        assertNoMasterDataRules(templateId);

        String actorId = CurrentActor.id();
        entity.setStatus(ConfigStatus.waitingForChecker);
        entity.setSubmittedBy(actorId);
        Template saved = templateRepository.save(entity);
        auditEventService.record("ADMIN_TEMPLATE_SUBMITTED", actorId, saved.getProcessId(), saved.getTemplateCode(),
                AuditOutcome.SUCCESS, "Template " + templateId + " submitted for review");
        return buildResponse(saved);
    }

    @Override
    @Transactional
    public TemplateResponse accept(String templateId) {
        log.debug("Accepting template: id={}", templateId);
        Template entity = findOrThrow(templateId);
        assertProcessExistsAndNotLocked(entity.getProcessId());
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        assertNoMasterDataRules(templateId);
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.active);
        entity.setRejectionReason(null);
        Template saved = templateRepository.save(entity);
        TemplateResponse response = buildResponse(saved);
        // Unconditional, unlike create/update/clone's captureVersionSnapshotIfNew: the version
        // number does not change between submit and accept, so a "skip if this version already
        // has a snapshot" guard would always skip here — the approved content would never be
        // captured. See V1_0_57 for why more than one row per version is fine.
        captureVersionSnapshot(saved, response, actorId);
        auditEventService.record("ADMIN_TEMPLATE_ACTIVATED", actorId, saved.getProcessId(), saved.getTemplateCode(),
                AuditOutcome.SUCCESS, "Template " + templateId + " activated");
        return response;
    }

    @Override
    @Transactional
    public TemplateResponse reject(String templateId, RejectRequest request) {
        log.debug("Rejecting template: id={}", templateId);
        Template entity = findOrThrow(templateId);
        assertProcessExistsAndNotLocked(entity.getProcessId());
        ConfigLifecycleGuard.assertWaitingForChecker(entity.getStatus());
        String actorId = CurrentActor.id();
        ConfigLifecycleGuard.assertFourEyes(entity.getSubmittedBy(), actorId);
        entity.setStatus(ConfigStatus.rejected);
        entity.setRejectionReason(request.reason());
        Template saved = templateRepository.save(entity);
        auditEventService.record("ADMIN_TEMPLATE_REJECTED", actorId, saved.getProcessId(), saved.getTemplateCode(),
                AuditOutcome.SUCCESS, "Template " + templateId + " rejected: " + request.reason());
        return buildResponse(saved);
    }

    @Override
    @Transactional
    public TemplateResponse clone(String templateId, CloneTemplateRequest request) {
        log.debug("Cloning template: id={}", templateId);
        Template source = findOrThrow(templateId);
        assertProcessExistsAndNotLocked(source.getProcessId());
        String actorId = CurrentActor.id();

        Template copy = new Template();
        copy.setTemplateId(nextTemplateId());
        copy.setTemplateCode(generateTemplateCode());
        copy.setTemplateName(request.newName());
        copy.setTemplateDescription(source.getTemplateDescription());
        copy.setVersion("1.0.0");
        copy.setProcessId(source.getProcessId());
        copy.setStatus(ConfigStatus.draft);
        copy.setPackageMaxSizeMb(source.getPackageMaxSizeMb());
        copy.setPackageMaxRows(source.getPackageMaxRows());
        copy.setDuplicateAction(source.getDuplicateAction());
        copy.setRowOrder(source.getRowOrder());
        copy.setPostLoadActionType(source.getPostLoadActionType());
        copy.setKafkaTopic(source.getKafkaTopic());
        copy.setKafkaBootstrapServers(source.getKafkaBootstrapServers());
        copy.setDatabaseMode(source.getDatabaseMode());
        copy.setDatabaseConnectionId(source.getDatabaseConnectionId());
        copy.setDatabaseProvider(source.getDatabaseProvider());
        copy.setDatabaseConnectionRef(source.getDatabaseConnectionRef());
        copy.setDatabaseTableName(source.getDatabaseTableName());
        copy.setUploadProcessTimeoutMinutes(source.getUploadProcessTimeoutMinutes());
        copy.setValidationWorkerThreads(source.getValidationWorkerThreads());
        copy.setValidationsEnabled(source.isValidationsEnabled());
        copy.setMakerCheckerEnabled(source.isMakerCheckerEnabled());
        copy.setMakerCheckerActorNeSubmitter(source.isMakerCheckerActorNeSubmitter());
        copy.setMakerCheckerSlaHours(source.getMakerCheckerSlaHours());
        copy.setMakerCheckerEscalateToRole(source.getMakerCheckerEscalateToRole());
        copy.setFailFast(source.isFailFast());
        copy.setSchedule(source.getSchedule());
        copy.setCreatedBy(actorId);

        Template savedCopy = templateRepository.saveAndFlush(copy);
        String newTemplateId = savedCopy.getTemplateId();

        // The seed trigger already inserted 3 default format rows for savedCopy — replace them
        // with the source template's actual settings.
        templateUploadFormatRepository.deleteByTemplateId(newTemplateId);
        templateUploadFormatRepository.flush();
        templateUploadFormatRepository.saveAll(
                cloneFormats(templateUploadFormatRepository.findByTemplateId(templateId), newTemplateId));

        templateFieldRepository.saveAll(
                cloneFields(templateFieldRepository.findByTemplateIdOrderBySortOrder(templateId), newTemplateId));
        templatePkFieldRepository.saveAll(
                clonePkFields(templatePkFieldRepository.findByTemplateIdOrderBySortOrder(templateId), newTemplateId));
        templateSortFieldRepository.saveAll(
                cloneSortFields(templateSortFieldRepository.findByTemplateIdOrderBySortOrder(templateId), newTemplateId));
        templateCheckerRoleRepository.saveAll(
                cloneCheckerRoles(templateCheckerRoleRepository.findByTemplateId(templateId), newTemplateId));
        templateTransformationRepository.saveAll(cloneTransformations(
                templateTransformationRepository.findByTemplateIdOrderBySortOrder(templateId), newTemplateId));
        templateValidationRuleRepository.saveAll(
                cloneRules(templateValidationRuleRepository.findByTemplateIdOrderBySortOrder(templateId), newTemplateId));

        TemplateResponse response = buildResponse(savedCopy);
        captureVersionSnapshotIfNew(savedCopy, response, actorId);
        auditEventService.record("ADMIN_TEMPLATE_CLONED", actorId, savedCopy.getProcessId(), savedCopy.getTemplateCode(),
                AuditOutcome.SUCCESS, "Template " + templateId + " cloned to " + newTemplateId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateVersionSnapshotResponse> listVersions(String templateId) {
        log.debug("Listing template versions: id={}", templateId);
        return templateVersionSnapshotRepository.findByTemplateIdOrderByCapturedAtDesc(templateId).stream()
                .map(this::toSnapshotResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateVersionSnapshotResponse getVersion(String templateId, String version) {
        log.debug("Fetching template version: id={}, version={}", templateId, version);
        TemplateVersionSnapshot snapshot = templateVersionSnapshotRepository
                .findFirstByTemplateIdAndVersionOrderByCapturedAtDesc(templateId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + version + " was never captured for template " + templateId));
        return toSnapshotResponse(snapshot);
    }

    private TemplateVersionSnapshotResponse toSnapshotResponse(TemplateVersionSnapshot snapshot) {
        TemplateResponse captured = JsonColumnMapper.read(snapshot.getSnapshot(), TemplateResponse.class);
        return new TemplateVersionSnapshotResponse(snapshot.getSnapshotId(), snapshot.getTemplateId(),
                snapshot.getVersion(), captured, snapshot.getCapturedBy(), snapshot.getCapturedAt());
    }

    private TemplateResponse buildResponse(Template entity) {
        String templateId = entity.getTemplateId();
        List<TemplateField> fields = templateFieldRepository.findByTemplateIdOrderBySortOrder(templateId);
        List<TemplateUploadFormat> formats = templateUploadFormatRepository.findByTemplateId(templateId);
        List<TemplatePkField> pkFields = templatePkFieldRepository.findByTemplateIdOrderBySortOrder(templateId);
        List<TemplateSortField> sortFields = templateSortFieldRepository.findByTemplateIdOrderBySortOrder(templateId);
        List<TemplateCheckerRole> checkerRoles = templateCheckerRoleRepository.findByTemplateId(templateId);
        List<TemplateTransformation> transformations = templateTransformationRepository.findByTemplateIdOrderBySortOrder(templateId);
        List<TemplateValidationRule> rules = templateValidationRuleRepository.findByTemplateIdOrderBySortOrder(templateId);
        return templateMapper.toResponse(entity, fields, formats, pkFields, sortFields, checkerRoles, transformations, rules);
    }

    private void replaceChildren(String templateId, UpdateTemplateRequest request) {
        templateFieldRepository.deleteByTemplateId(templateId);
        templateFieldRepository.flush();
        templateFieldRepository.saveAll(templateMapper.toFieldEntities(request.fields(), templateId));

        templateUploadFormatRepository.deleteByTemplateId(templateId);
        templateUploadFormatRepository.flush();
        templateUploadFormatRepository.saveAll(templateMapper.toUploadFormatEntities(request.uploadFormats(), templateId));

        templatePkFieldRepository.deleteByTemplateId(templateId);
        templatePkFieldRepository.flush();
        templatePkFieldRepository.saveAll(templateMapper.toPkFieldEntities(request.dataLoad().primaryKeyFields(), templateId));

        templateSortFieldRepository.deleteByTemplateId(templateId);
        templateSortFieldRepository.flush();
        templateSortFieldRepository.saveAll(templateMapper.toSortFieldEntities(request.dataLoad().sortFields(), templateId));

        templateCheckerRoleRepository.deleteByTemplateId(templateId);
        templateCheckerRoleRepository.flush();
        templateCheckerRoleRepository.saveAll(templateMapper.toCheckerRoleEntities(request.makerChecker().checkerRoles(), templateId));

        templateTransformationRepository.deleteByTemplateId(templateId);
        templateTransformationRepository.flush();
        templateTransformationRepository.saveAll(templateMapper.toTransformationEntities(request.transformations(), templateId));

        templateValidationRuleRepository.deleteByTemplateId(templateId);
        templateValidationRuleRepository.flush();
        templateValidationRuleRepository.saveAll(templateMapper.toValidationRuleEntities(request.rules(), templateId));
    }

    /**
     * Create/update/clone only capture the first save at a given version — draft churn before a
     * template is ever activated all shares one version number, and re-capturing an identical
     * near-duplicate on every keystroke-driven save would bury the meaningful history. {@link
     * #accept} captures unconditionally via {@link #captureVersionSnapshot} instead, since that
     * moment (the approved content) would otherwise never be recorded at all — see V1_0_57.
     */
    private void captureVersionSnapshotIfNew(Template entity, TemplateResponse response, String actorId) {
        if (templateVersionSnapshotRepository.existsByTemplateIdAndVersion(entity.getTemplateId(), entity.getVersion())) {
            return;
        }
        captureVersionSnapshot(entity, response, actorId);
    }

    private void captureVersionSnapshot(Template entity, TemplateResponse response, String actorId) {
        TemplateVersionSnapshot snapshot = new TemplateVersionSnapshot();
        snapshot.setSnapshotId(IdGenerator.generate("ver"));
        snapshot.setTemplateId(entity.getTemplateId());
        snapshot.setVersion(entity.getVersion());
        snapshot.setSnapshot(JsonColumnMapper.write(response));
        snapshot.setCapturedBy(actorId);
        templateVersionSnapshotRepository.save(snapshot);
    }

    private String generateTemplateCode() {
        return "TPL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** Sequential, DB-generated (template_id_seq, V1_3_2) — e.g. tmpl-000001. */
    private String nextTemplateId() {
        return IdGenerator.fromSequence("tmpl", templateRepository.nextTemplateIdSequence());
    }

    private String bumpPatchVersion(String version) {
        String[] parts = version.split("\\.");
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }

    private void assertAtLeastOneFormatEnabled(UpdateTemplateRequest request) {
        boolean anyEnabled = request.uploadFormats().xlsx().enabled()
                || request.uploadFormats().csv().enabled()
                || request.uploadFormats().json().enabled();
        if (!anyEnabled) {
            throw new ConflictException("At least one upload format must be enabled");
        }
    }

    private void assertAtLeastOneFormatEnabledPersisted(String templateId) {
        boolean anyEnabled = templateUploadFormatRepository.findByTemplateId(templateId).stream()
                .anyMatch(TemplateUploadFormat::isEnabled);
        if (!anyEnabled) {
            throw new ConflictException("At least one upload format must be enabled");
        }
    }

    private void assertNoMasterDataRules(String templateId) {
        boolean hasMasterData = templateValidationRuleRepository.findByTemplateIdOrderBySortOrder(templateId).stream()
                .anyMatch(rule -> rule.getRuleType() == ValidationRuleType.MASTER_DATA);
        if (hasMasterData) {
            throw new ConflictException("MASTER_DATA validation rules are reserved (coming soon) and block submit/accept");
        }
    }

    private void assertProcessExistsAndNotLocked(String processId) {
        if (!uploadProcessRepository.existsById(processId)) {
            throw new ResourceNotFoundException("Process not found with id: " + processId);
        }
        if (configLockService.isLocked(processId)) {
            throw new ConfigLockedException("Config locked for process " + processId);
        }
    }

    private Template findOrThrow(String templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + templateId));
    }

    private List<TemplateField> cloneFields(List<TemplateField> source, String newTemplateId) {
        return source.stream().map(f -> {
            TemplateField copy = new TemplateField();
            copy.setFieldId(IdGenerator.generate("fld"));
            copy.setTemplateId(newTemplateId);
            copy.setSourceColumn(f.getSourceColumn());
            copy.setTargetField(f.getTargetField());
            copy.setFieldLabel(f.getFieldLabel());
            copy.setFieldType(f.getFieldType());
            copy.setRequired(f.isRequired());
            copy.setSortOrder(f.getSortOrder());
            return copy;
        }).toList();
    }

    private List<TemplateUploadFormat> cloneFormats(List<TemplateUploadFormat> source, String newTemplateId) {
        return source.stream().map(f -> {
            TemplateUploadFormat copy = new TemplateUploadFormat();
            copy.setTemplateId(newTemplateId);
            copy.setFormatKey(f.getFormatKey());
            copy.setEnabled(f.isEnabled());
            copy.setMaxSizeMb(f.getMaxSizeMb());
            copy.setSheetName(f.getSheetName());
            copy.setDelimiter(f.getDelimiter());
            copy.setCharset(f.getCharset());
            copy.setHeaderRow(f.getHeaderRow());
            copy.setRootArrayPath(f.getRootArrayPath());
            return copy;
        }).toList();
    }

    private List<TemplatePkField> clonePkFields(List<TemplatePkField> source, String newTemplateId) {
        return source.stream().map(f -> {
            TemplatePkField copy = new TemplatePkField();
            copy.setTemplateId(newTemplateId);
            copy.setTargetField(f.getTargetField());
            copy.setSortOrder(f.getSortOrder());
            return copy;
        }).toList();
    }

    private List<TemplateSortField> cloneSortFields(List<TemplateSortField> source, String newTemplateId) {
        return source.stream().map(f -> {
            TemplateSortField copy = new TemplateSortField();
            copy.setTemplateId(newTemplateId);
            copy.setTargetField(f.getTargetField());
            copy.setDirection(f.getDirection());
            copy.setSortOrder(f.getSortOrder());
            return copy;
        }).toList();
    }

    private List<TemplateCheckerRole> cloneCheckerRoles(List<TemplateCheckerRole> source, String newTemplateId) {
        return source.stream().map(r -> {
            TemplateCheckerRole copy = new TemplateCheckerRole();
            copy.setTemplateId(newTemplateId);
            copy.setRoleRef(r.getRoleRef());
            return copy;
        }).toList();
    }

    private List<TemplateTransformation> cloneTransformations(List<TemplateTransformation> source, String newTemplateId) {
        return source.stream().map(t -> {
            TemplateTransformation copy = new TemplateTransformation();
            copy.setTemplateId(newTemplateId);
            copy.setTargetField(t.getTargetField());
            copy.setMappings(t.getMappings());
            copy.setSortOrder(t.getSortOrder());
            return copy;
        }).toList();
    }

    private List<TemplateValidationRule> cloneRules(List<TemplateValidationRule> source, String newTemplateId) {
        return source.stream().map(r -> {
            TemplateValidationRule copy = new TemplateValidationRule();
            copy.setRuleId(IdGenerator.generate("R"));
            copy.setTemplateId(newTemplateId);
            copy.setField(r.getField());
            copy.setRuleType(r.getRuleType());
            copy.setSeverity(r.getSeverity());
            copy.setMessage(r.getMessage());
            copy.setProfile(r.getProfile());
            copy.setPattern(r.getPattern());
            copy.setFormat(r.getFormat());
            copy.setRequired(r.getRequired());
            copy.setRejectEmptyString(r.getRejectEmptyString());
            copy.setRejectWhitespace(r.getRejectWhitespace());
            copy.setAllowedValues(r.getAllowedValues());
            copy.setCaseInsensitive(r.getCaseInsensitive());
            copy.setDecimalPlaces(r.getDecimalPlaces());
            copy.setDelimiter(r.getDelimiter());
            copy.setMinValue(r.getMinValue());
            copy.setMaxValue(r.getMaxValue());
            copy.setExpression(r.getExpression());
            copy.setFormulaTerms(r.getFormulaTerms());
            copy.setFormulaOperators(r.getFormulaOperators());
            copy.setCompareOperator(r.getCompareOperator());
            copy.setGroupByField(r.getGroupByField());
            copy.setTransactionSplit(r.getTransactionSplit());
            copy.setCondition(r.getCondition());
            copy.setSortOrder(r.getSortOrder());
            return copy;
        }).toList();
    }
}
