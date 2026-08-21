package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.CloneTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.CreateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UpdateTemplateRequest;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateSummaryResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateVersionSnapshotResponse;
import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import java.util.List;

public interface TemplateService {

    List<TemplateSummaryResponse> listByProcess(String processId, ConfigStatus status);

    TemplateResponse getById(String templateId);

    TemplateResponse create(String processId, CreateTemplateRequest request);

    TemplateResponse update(String templateId, UpdateTemplateRequest request);

    TemplateResponse submit(String templateId);

    TemplateResponse accept(String templateId);

    TemplateResponse reject(String templateId, RejectRequest request);

    TemplateResponse clone(String templateId, CloneTemplateRequest request);

    List<TemplateVersionSnapshotResponse> listVersions(String templateId);

    TemplateVersionSnapshotResponse getVersion(String templateId, String version);
}
