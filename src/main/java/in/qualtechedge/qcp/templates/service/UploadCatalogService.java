package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.BlankTemplateFileResponse;
import in.qualtechedge.qcp.templates.dto.response.ProcessResponse;
import in.qualtechedge.qcp.templates.dto.response.TemplateResponse;
import java.util.List;

/** §1 Processes & Templates (read-only, upload context). */
public interface UploadCatalogService {

    List<ProcessResponse> listPermittedProcesses(String makerUserId);

    TemplateResponse getActiveTemplate(String processId);

    BlankTemplateFileResponse downloadBlankTemplate(String processId, String format);
}
