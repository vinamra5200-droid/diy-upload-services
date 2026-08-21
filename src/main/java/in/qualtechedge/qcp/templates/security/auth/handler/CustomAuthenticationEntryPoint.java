package in.qualtechedge.qcp.templates.security.auth.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Returns a structured JSON 401 response instead of the default Spring Security HTML page.
 * Matches the QCP standard error envelope so clients receive a consistent error contract.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "ERROR");
        errorResponse.put("statusCode", HttpStatus.UNAUTHORIZED.value());
        errorResponse.put("errorCode", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        errorResponse.put("errorMessage", "Authentication token is missing or invalid");
        errorResponse.put("timestamp", ZonedDateTime.now().toString());
        errorResponse.put("requestId", UUID.randomUUID().toString());

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
