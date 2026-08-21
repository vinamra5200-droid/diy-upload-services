package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.request.UploadRoleRequest;
import in.qualtechedge.qcp.templates.dto.response.UploadRoleResponse;
import java.util.List;

public interface UploadRoleService {

    UploadRoleResponse create(UploadRoleRequest request);

    UploadRoleResponse getById(String roleId);

    List<UploadRoleResponse> getAll();

    UploadRoleResponse update(String roleId, UploadRoleRequest request);

    UploadRoleResponse submit(String roleId);

    UploadRoleResponse accept(String roleId);

    UploadRoleResponse reject(String roleId, RejectRequest request);
}
