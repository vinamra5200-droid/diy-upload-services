package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.MakerUserRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.MakerUserResponse;
import java.util.List;

public interface MakerUserService {

    MakerUserResponse create(MakerUserRequest request);

    MakerUserResponse getById(String userId);

    List<MakerUserResponse> getAll();

    MakerUserResponse update(String userId, MakerUserRequest request);

    MakerUserResponse submit(String userId);

    MakerUserResponse accept(String userId);

    MakerUserResponse reject(String userId, RejectRequest request);
}
