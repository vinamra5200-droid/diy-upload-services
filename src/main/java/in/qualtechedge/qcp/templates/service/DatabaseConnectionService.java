package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.request.DatabaseConnectionRequest;
import in.qualtechedge.qcp.templates.dto.request.RejectRequest;
import in.qualtechedge.qcp.templates.dto.response.DatabaseConnectionResponse;
import java.util.List;

public interface DatabaseConnectionService {

    DatabaseConnectionResponse create(DatabaseConnectionRequest request);

    DatabaseConnectionResponse getById(String connectionId);

    List<DatabaseConnectionResponse> getAll();

    DatabaseConnectionResponse update(String connectionId, DatabaseConnectionRequest request);

    DatabaseConnectionResponse submit(String connectionId);

    DatabaseConnectionResponse accept(String connectionId);

    DatabaseConnectionResponse reject(String connectionId, RejectRequest request);
}
