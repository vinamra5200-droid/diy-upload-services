package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.response.BlankTemplateFileResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.entity.MakerUser;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.TemplateField;
import in.qualtechedge.qcp.templates.entity.UploadProcess;
import in.qualtechedge.qcp.templates.entity.UploadRole;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.enums.UploadFormatKey;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.exception.UnprocessableEntityException;
import in.qualtechedge.qcp.templates.mapper.ProcessMapper;
import in.qualtechedge.qcp.templates.repository.TemplateFieldRepository;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.MakerUserRepository;
import in.qualtechedge.qcp.templates.repository.TemplateUploadFormatRepository;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.repository.UploadRoleRepository;
import in.qualtechedge.qcp.templates.service.ConfigLockService;
import in.qualtechedge.qcp.templates.service.TemplateService;
import in.qualtechedge.qcp.templates.service.UploadCatalogService;
import in.qualtechedge.qcp.templates.utils.BlankTemplateWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadCatalogServiceImpl implements UploadCatalogService {

    private final UploadProcessRepository uploadProcessRepository;
    private final TemplateRepository templateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final TemplateUploadFormatRepository templateUploadFormatRepository;
    private final MakerUserRepository makerUserRepository;
    private final UploadRoleRepository uploadRoleRepository;
    private final TemplateService templateService;
    private final ProcessMapper processMapper;
    private final ConfigLockService configLockService;

    @Override
    @Transactional(readOnly = true)
    public List<ProcessResponse> listPermittedProcesses(String makerUserId) {
        log.debug("Listing permitted processes: makerUserId={}", makerUserId);
        Set<String> processIds = permittedProcessIds(makerUserId);
        if (processIds.isEmpty()) {
            return List.of();
        }
        return uploadProcessRepository.findByStatusAndProcessIdIn(ConfigStatus.active, processIds).stream()
                .map(entity -> processMapper.toResponse(entity, configLockService.isLocked(entity.getProcessId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getActiveTemplate(String processId) {
        log.debug("Fetching active template: processId={}", processId);
        Template template = templateRepository.findFirstByProcessIdAndStatus(processId, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active template for process " + processId));
        return templateService.getById(template.getTemplateId());
    }

    @Override
    @Transactional(readOnly = true)
    public BlankTemplateFileResponse downloadBlankTemplate(String processId, String format) {
        log.debug("Downloading blank template: processId={}, format={}", processId, format);
        Template template = templateRepository.findFirstByProcessIdAndStatus(processId, ConfigStatus.active)
                .orElseThrow(() -> new ResourceNotFoundException("No active template for process " + processId));
        UploadFormatKey formatKey = parseFormat(format);
        List<String> headers = templateFieldRepository.findByTemplateIdOrderBySortOrder(template.getTemplateId())
                .stream().map(TemplateField::getSourceColumn).toList();

        return switch (formatKey) {
            case xlsx -> {
                String sheetName = templateUploadFormatRepository.findByTemplateId(template.getTemplateId()).stream()
                        .filter(f -> f.getFormatKey() == UploadFormatKey.xlsx)
                        .findFirst().map(f -> f.getSheetName()).orElse(null);
                yield new BlankTemplateFileResponse(BlankTemplateWriter.writeXlsx(headers, sheetName),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        template.getTemplateCode() + "_blank.xlsx");
            }
            case csv -> new BlankTemplateFileResponse(BlankTemplateWriter.writeCsv(headers), "text/csv",
                    template.getTemplateCode() + "_blank.csv");
            case json -> {
                String rootArrayPath = templateUploadFormatRepository.findByTemplateId(template.getTemplateId()).stream()
                        .filter(f -> f.getFormatKey() == UploadFormatKey.json)
                        .findFirst().map(f -> f.getRootArrayPath()).orElse(null);
                yield new BlankTemplateFileResponse(BlankTemplateWriter.writeJson(rootArrayPath), "application/json",
                        template.getTemplateCode() + "_blank.json");
            }
        };
    }

    private UploadFormatKey parseFormat(String format) {
        try {
            return UploadFormatKey.valueOf(format);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnprocessableEntityException("Unsupported blank-template format: " + format);
        }
    }

    private Set<String> permittedProcessIds(String makerUserId) {
        MakerUser makerUser = makerUserRepository.findById(makerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Maker user not found with id: " + makerUserId));
        Set<String> processIds = new LinkedHashSet<>();
        for (UploadRole role : uploadRoleRepository.findAllById(makerUser.getRoleIds())) {
            processIds.addAll(role.getProcessAccess());
        }
        return processIds;
    }
}
