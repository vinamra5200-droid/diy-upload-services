package in.qualtechedge.qcp.templates.service.keycloak;

import in.qualtechedge.qcp.templates.dto.request.LoginRequest;
import in.qualtechedge.qcp.templates.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface UserAuthService {
    LoginResponse keycloakUserLogin(LoginRequest loginRequest, HttpServletRequest request);
}
