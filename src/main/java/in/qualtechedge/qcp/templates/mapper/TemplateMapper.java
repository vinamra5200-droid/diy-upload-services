package in.qualtechedge.qcp.templates.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import in.qualtechedge.qcp.templates.dto.request.CreateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.DataLoadRequest;
import in.qualtechedge.qcp.templates.dto.request.TemplateFieldRequest;
import in.qualtechedge.qcp.templates.dto.request.TransformationRequest;
import in.qualtechedge.qcp.templates.dto.request.UpdateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.UploadFormatsRequest;
import in.qualtechedge.qcp.templates.dto.request.ValidationRuleRequest;
import in.qualtechedge.qcp.templates.dto.response.DataLoadResponse;
import in.qualtechedge.qcp.templates.dto.response.MakerCheckerResponse;
import in.qualtechedge.qcp.templates.dto.response.PackageGateResponse;
import in.qualtechedge.qcp.templates.dto.response.PostLoadActionResponse;
import in.qualtechedge.qcp.templates.dto.response.ScheduleResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateFieldResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.TransformationResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadFormatsResponse;
import in.qualtechedge.qcp.templates.dto.response.ValidationRuleResponse;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.TemplateCheckerRole;
import in.qualtechedge.qcp.templates.entity.TemplateField;
import in.qualtechedge.qcp.templates.entity.TemplatePkField;
import in.qualtechedge.qcp.templates.entity.TemplateSortField;
import in.qualtechedge.qcp.templates.entity.TemplateTransformation;
import in.qualtechedge.qcp.templates.entity.TemplateUploadFormat;
import in.qualtechedge.qcp.templates.entity.TemplateValidationRule;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import in.qualtechedge.qcp.templates.utils.JsonColumnMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Assembles/flattens the {@code Template} aggregate. Child collections are converted here but
 * persisted/queried by {@link in.qualtechedge.qcp.templates.service.impl.TemplateServiceImpl}
 * through their own repositories — see {@link Template}'s class comment for why.
 */
@Component
public class TemplateMapper {

    public Template toEntity(CreateTemplateRequest request, String processId, String createdBy) {
        Template entity = new Template();
        entity.setTemplateId(IdGenerator.generate("tmpl"));
        entity.setTemplateCode(generateTemplateCode());
        entity.setTemplateName(request.templateName());
        entity.setTemplateDescription(request.templateDescription() == null ? "" : request.templateDescription());
        entity.setProcessId(processId);
        entity.setStatus(ConfigStatus.draft);
        entity.setMakerCheckerEnabled(Boolean.TRUE.equals(request.makerCheckerEnabled()));
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private String generateTemplateCode() {
        return "TPL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public void applyUpdate(Template entity, UpdateTemplateRequest request) {
        entity.setTemplateName(request.templateName());
        entity.setTemplateDescription(request.templateDescription());
        entity.setPackageMaxSizeMb(request.packageGate().maxSizeMb());
        entity.setPackageMaxRows(request.packageGate().maxRows());
        entity.setDuplicateAction(request.dataLoad().duplicateAction());
        entity.setRowOrder(request.dataLoad().rowOrder());
        entity.setPostLoadActionType(request.postLoadAction().type());
        entity.setKafkaTopic(request.postLoadAction().kafkaTopic());
        entity.setKafkaBootstrapServers(request.postLoadAction().kafkaBootstrapServers());
        entity.setDatabaseMode(request.postLoadAction().databaseMode());
        entity.setDatabaseConnectionId(request.postLoadAction().databaseConnectionId());
        entity.setDatabaseProvider(request.postLoadAction().databaseProvider());
        entity.setDatabaseConnectionRef(request.postLoadAction().databaseConnectionRef());
        entity.setDatabaseTableName(request.postLoadAction().databaseTableName());
        entity.setUploadProcessTimeoutMinutes(request.uploadProcessTimeoutMinutes());
        entity.setValidationWorkerThreads(request.validationWorkerThreads());
        entity.setValidationsEnabled(Boolean.TRUE.equals(request.validationsEnabled()));
        entity.setMakerCheckerEnabled(request.makerChecker().enabled());
        entity.setMakerCheckerActorNeSubmitter(request.makerChecker().actorNeSubmitter());
        entity.setMakerCheckerSlaHours(request.makerChecker().slaHours());
        entity.setMakerCheckerEscalateToRole(
                request.makerChecker().escalateToRole() == null ? "" : request.makerChecker().escalateToRole());
        entity.setFailFast(Boolean.TRUE.equals(request.failFast()));
        entity.setSchedule(request.schedule() == null ? null : JsonColumnMapper.write(request.schedule()));
    }

    public TemplateResponse toResponse(Template entity, List<TemplateField> fields, List<TemplateUploadFormat> uploadFormats,
            List<TemplatePkField> pkFields, List<TemplateSortField> sortFields, List<TemplateCheckerRole> checkerRoles,
            List<TemplateTransformation> transformations, List<TemplateValidationRule> rules) {
        MakerCheckerResponse makerChecker = new MakerCheckerResponse(entity.isMakerCheckerEnabled(),
                toCheckerRoleStrings(checkerRoles), entity.isMakerCheckerActorNeSubmitter(),
                entity.getMakerCheckerSlaHours(), entity.getMakerCheckerEscalateToRole());
        DataLoadResponse dataLoad = new DataLoadResponse(toPkFieldStrings(pkFields), entity.getDuplicateAction(),
                entity.getRowOrder(), toSortFieldResponses(sortFields));
        PostLoadActionResponse postLoadAction = new PostLoadActionResponse(entity.getPostLoadActionType(),
                entity.getKafkaTopic(), entity.getKafkaBootstrapServers(), entity.getDatabaseMode(),
                entity.getDatabaseConnectionId(), entity.getDatabaseTableName(), entity.getDatabaseProvider(),
                entity.getDatabaseConnectionRef());
        PackageGateResponse packageGate = new PackageGateResponse(entity.getPackageMaxSizeMb(), entity.getPackageMaxRows());
        ScheduleResponse schedule = entity.getSchedule() == null
                ? null
                : JsonColumnMapper.read(entity.getSchedule(), ScheduleResponse.class);

        return new TemplateResponse(
                entity.getTemplateId(), entity.getTemplateCode(), entity.getTemplateName(), entity.getTemplateDescription(),
                entity.getVersion(), entity.getProcessId(), entity.getStatus(),
                toFieldResponses(fields), toUploadFormatsResponse(uploadFormats), packageGate, dataLoad, postLoadAction,
                entity.getUploadProcessTimeoutMinutes(), entity.getValidationWorkerThreads(), makerChecker,
                toTransformationResponses(transformations), entity.isValidationsEnabled(), toValidationRuleResponses(rules),
                entity.isFailFast(), schedule, entity.getSubmittedBy(), entity.getRejectionReason(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public TemplateSummaryResponse toSummaryResponse(Template entity, int fieldsCount, int rulesCount) {
        return new TemplateSummaryResponse(entity.getTemplateId(), entity.getTemplateCode(), entity.getTemplateName(),
                entity.getVersion(), entity.getProcessId(), entity.getStatus(), fieldsCount, rulesCount,
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    // ---- fields ----

    public List<TemplateField> toFieldEntities(List<TemplateFieldRequest> fields, String templateId) {
        List<TemplateField> entities = new ArrayList<>();
        int order = 0;
        for (TemplateFieldRequest field : fields) {
            TemplateField entity = new TemplateField();
            entity.setFieldId(IdGenerator.generate("fld"));
            entity.setTemplateId(templateId);
            entity.setSourceColumn(field.sourceColumn());
            entity.setTargetField(field.targetField());
            entity.setFieldLabel(field.fieldLabel());
            entity.setFieldType(field.fieldType());
            entity.setRequired(field.required());
            entity.setSortOrder(order++);
            entities.add(entity);
        }
        return entities;
    }

    public List<TemplateFieldResponse> toFieldResponses(List<TemplateField> fields) {
        return fields.stream()
                .map(f -> new TemplateFieldResponse(f.getSourceColumn(), f.getTargetField(), f.getFieldLabel(), f.getFieldType(), f.isRequired()))
                .toList();
    }

    // ---- upload formats ----

    public List<TemplateUploadFormat> toUploadFormatEntities(UploadFormatsRequest request, String templateId) {
        return List.of(
                toFormatEntity(templateId, UploadFormatKey.xlsx, request.xlsx()),
                toFormatEntity(templateId, UploadFormatKey.csv, request.csv()),
                toFormatEntity(templateId, UploadFormatKey.json, request.json()));
    }

    private TemplateUploadFormat toFormatEntity(String templateId, UploadFormatKey key, UploadFormatsRequest.Entry entry) {
        TemplateUploadFormat format = new TemplateUploadFormat();
        format.setTemplateId(templateId);
        format.setFormatKey(key);
        format.setEnabled(entry.enabled());
        format.setMaxSizeMb(entry.maxSizeMb());
        format.setSheetName(entry.sheetName());
        format.setDelimiter(entry.delimiter());
        format.setCharset(entry.charset());
        format.setHeaderRow(entry.headerRow());
        format.setRootArrayPath(entry.rootArrayPath());
        return format;
    }

    public UploadFormatsResponse toUploadFormatsResponse(List<TemplateUploadFormat> formats) {
        Map<UploadFormatKey, TemplateUploadFormat> byKey = formats.stream()
                .collect(Collectors.toMap(TemplateUploadFormat::getFormatKey, f -> f));
        return new UploadFormatsResponse(
                toFormatEntry(byKey.get(UploadFormatKey.xlsx)),
                toFormatEntry(byKey.get(UploadFormatKey.csv)),
                toFormatEntry(byKey.get(UploadFormatKey.json)));
    }

    private UploadFormatsResponse.Entry toFormatEntry(TemplateUploadFormat format) {
        if (format == null) {
            return null;
        }
        return new UploadFormatsResponse.Entry(format.isEnabled(), format.getMaxSizeMb(), format.getSheetName(),
                format.getDelimiter(), format.getCharset(), format.getHeaderRow(), format.getRootArrayPath());
    }

    // ---- primary key fields ----

    public List<TemplatePkField> toPkFieldEntities(List<String> primaryKeyFields, String templateId) {
        List<TemplatePkField> entities = new ArrayList<>();
        int order = 0;
        for (String field : primaryKeyFields) {
            TemplatePkField entity = new TemplatePkField();
            entity.setTemplateId(templateId);
            entity.setTargetField(field);
            entity.setSortOrder(order++);
            entities.add(entity);
        }
        return entities;
    }

    public List<String> toPkFieldStrings(List<TemplatePkField> pkFields) {
        return pkFields.stream().map(TemplatePkField::getTargetField).toList();
    }

    // ---- sort fields ----

    public List<TemplateSortField> toSortFieldEntities(List<DataLoadRequest.SortFieldEntry> sortFields, String templateId) {
        List<TemplateSortField> entities = new ArrayList<>();
        int order = 0;
        for (DataLoadRequest.SortFieldEntry sortField : sortFields) {
            TemplateSortField entity = new TemplateSortField();
            entity.setTemplateId(templateId);
            entity.setTargetField(sortField.field());
            entity.setDirection(sortField.direction());
            entity.setSortOrder(order++);
            entities.add(entity);
        }
        return entities;
    }

    public List<DataLoadResponse.SortFieldEntry> toSortFieldResponses(List<TemplateSortField> sortFields) {
        return sortFields.stream().map(f -> new DataLoadResponse.SortFieldEntry(f.getTargetField(), f.getDirection())).toList();
    }

    // ---- checker roles ----

    public List<TemplateCheckerRole> toCheckerRoleEntities(List<String> checkerRoles, String templateId) {
        return checkerRoles.stream().map(role -> {
            TemplateCheckerRole entity = new TemplateCheckerRole();
            entity.setTemplateId(templateId);
            entity.setRoleRef(role);
            return entity;
        }).toList();
    }

    public List<String> toCheckerRoleStrings(List<TemplateCheckerRole> checkerRoles) {
        return checkerRoles.stream().map(TemplateCheckerRole::getRoleRef).toList();
    }

    // ---- transformations ----

    public List<TemplateTransformation> toTransformationEntities(List<TransformationRequest> transformations, String templateId) {
        List<TemplateTransformation> entities = new ArrayList<>();
        int order = 0;
        for (TransformationRequest transformation : transformations) {
            TemplateTransformation entity = new TemplateTransformation();
            entity.setTemplateId(templateId);
            entity.setTargetField(transformation.field());
            entity.setMappings(JsonColumnMapper.write(transformation.mappings() == null ? List.of() : transformation.mappings()));
            entity.setSortOrder(order++);
            entities.add(entity);
        }
        return entities;
    }

    public List<TransformationResponse> toTransformationResponses(List<TemplateTransformation> transformations) {
        return transformations.stream()
                .map(t -> new TransformationResponse(t.getTargetField(),
                        JsonColumnMapper.read(t.getMappings(), new TypeReference<List<TransformationResponse.Mapping>>() {
                        })))
                .toList();
    }

    // ---- validation rules ----

    public List<TemplateValidationRule> toValidationRuleEntities(List<ValidationRuleRequest> rules, String templateId) {
        List<TemplateValidationRule> entities = new ArrayList<>();
        int order = 0;
        for (ValidationRuleRequest rule : rules) {
            TemplateValidationRule entity = new TemplateValidationRule();
            entity.setRuleId(IdGenerator.generate("R"));
            entity.setTemplateId(templateId);
            entity.setField(rule.field() == null ? "" : rule.field());
            entity.setRuleType(rule.type());
            entity.setSeverity(rule.severity());
            entity.setMessage(rule.message() == null ? "" : rule.message());
            entity.setProfile(rule.profile());
            entity.setPattern(rule.pattern());
            entity.setFormat(rule.format());
            entity.setRequired(rule.required());
            entity.setRejectEmptyString(rule.rejectEmptyString());
            entity.setRejectWhitespace(rule.rejectWhitespace());
            entity.setAllowedValues(rule.allowedValues() == null ? null : rule.allowedValues().toArray(new String[0]));
            entity.setCaseInsensitive(rule.caseInsensitive());
            entity.setDecimalPlaces(rule.decimalPlaces());
            entity.setDelimiter(rule.delimiter());
            entity.setMinValue(rule.minValue());
            entity.setMaxValue(rule.maxValue());
            entity.setExpression(rule.expression());
            entity.setFormulaTerms(rule.formulaTerms() == null ? null : JsonColumnMapper.write(rule.formulaTerms()));
            entity.setFormulaOperators(rule.formulaOperators() == null ? null : JsonColumnMapper.write(rule.formulaOperators()));
            entity.setCompareOperator(rule.compareOperator());
            entity.setGroupByField(rule.groupByField());
            entity.setTransactionSplit(rule.transactionSplit() == null ? null : JsonColumnMapper.write(rule.transactionSplit()));
            entity.setCondition(rule.condition() == null ? null : JsonColumnMapper.write(rule.condition()));
            entity.setSortOrder(order++);
            entities.add(entity);
        }
        return entities;
    }

    public List<ValidationRuleResponse> toValidationRuleResponses(List<TemplateValidationRule> rules) {
        return rules.stream().map(this::toValidationRuleResponse).toList();
    }

    private ValidationRuleResponse toValidationRuleResponse(TemplateValidationRule rule) {
        List<ValidationRuleResponse.FormulaTerm> formulaTerms = rule.getFormulaTerms() == null
                ? null
                : JsonColumnMapper.read(rule.getFormulaTerms(), new TypeReference<List<ValidationRuleResponse.FormulaTerm>>() {
                });
        List<String> formulaOperators = rule.getFormulaOperators() == null
                ? null
                : JsonColumnMapper.read(rule.getFormulaOperators(), new TypeReference<List<String>>() {
                });
        ValidationRuleResponse.TransactionSplit transactionSplit = rule.getTransactionSplit() == null
                ? null
                : JsonColumnMapper.read(rule.getTransactionSplit(), ValidationRuleResponse.TransactionSplit.class);
        ValidationRuleResponse.Condition condition = rule.getCondition() == null
                ? null
                : JsonColumnMapper.read(rule.getCondition(), ValidationRuleResponse.Condition.class);
        return new ValidationRuleResponse(
                rule.getRuleId(), rule.getField(), rule.getRuleType(), rule.getSeverity(), rule.getMessage(),
                rule.getProfile(), rule.getPattern(), rule.getFormat(),
                rule.getRequired(), rule.getRejectEmptyString(), rule.getRejectWhitespace(),
                rule.getAllowedValues() == null ? null : List.of(rule.getAllowedValues()), rule.getCaseInsensitive(),
                rule.getDecimalPlaces(), rule.getDelimiter(), rule.getMinValue(), rule.getMaxValue(),
                rule.getExpression(), formulaTerms, formulaOperators,
                rule.getCompareOperator(), rule.getGroupByField(), transactionSplit, condition);
    }
}
