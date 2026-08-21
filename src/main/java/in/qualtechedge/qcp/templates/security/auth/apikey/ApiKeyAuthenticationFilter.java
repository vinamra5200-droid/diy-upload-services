package in.qualtechedge.qcp.templates.security.auth.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Handles API key / API secret authentication for machine-to-machine requests.
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiType = request.getHeader(ApiKeyAuthenticationToken.API_KEY_TYPE_HEADER);
        String apiKey = request.getHeader(ApiKeyAuthenticationToken.API_KEY_HEADER);
        String apiSecret = request.getHeader(ApiKeyAuthenticationToken.API_SECRET_HEADER);

        if (!StringUtils.hasText(apiType) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            log.debug("API key headers absent — passing through for {}; other auth mechanisms will handle it",
                    request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret)) {
            try {
                ApiKeyAuthenticationToken unauthenticated =
                        new ApiKeyAuthenticationToken(apiType, apiKey, apiSecret);
                unauthenticated.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                ApiKeyAuthenticationToken authenticated =
                        (ApiKeyAuthenticationToken) apiKeyAuthenticationProvider.authenticate(unauthenticated);
                SecurityContextHolder.getContext().setAuthentication(authenticated);

                log.debug("API key authentication successful: apiType={}, apiKey={}", apiType, apiKey);
            } catch (Exception e) {
                log.error("API key authentication failed: apiKey={}", apiKey, e);
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API credentials");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
