package in.qualtechedge.qcp.templates.security.auth.apikey;

import in.qualtechedge.qcp.templates.entity.common.ApiClientPrincipal;
import in.qualtechedge.qcp.templates.entity.common.RolePrincipal;
import in.qualtechedge.qcp.templates.service.apiClient.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates {@link ApiKeyAuthenticationToken} requests by verifying the
 * {@code X-API-Key} and {@code X-API-Secret} headers via {@link ApiClientService}.
 * <p>
 * Routes to the admin ({@code auth.api_clients}) or tenant ({@code api_clients} in the tenant DB)
 * repository depending on the current {@link in.qualtechedge.qcp.templates.multitenancy.context.HostContext}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiClientService apiClientService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        ApiKeyAuthenticationToken token = (ApiKeyAuthenticationToken) authentication;
        String apiType = token.getApiType();
        String apiKey = token.getApiKey();
        String apiSecret = (String) token.getCredentials();

        log.debug("Authenticating API key: apiType={}, apiKey={}", apiType, apiKey);

        try {
            ApiClientPrincipal client = apiClientService.authenticate(apiKey, apiSecret);

            List<SimpleGrantedAuthority> authorities = client.getApiClientUserRoles().stream()
                    .map(RolePrincipal::getName)
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            ApiKeyAuthenticationToken authenticated =
                    new ApiKeyAuthenticationToken(apiType, client, apiSecret, authorities);
            authenticated.setDetails(authentication.getDetails());
            log.debug("API key authentication successful: clientId={}, roles={}", apiKey, authorities);
            return authenticated;

        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("API key authentication failed: apiKey={}", apiKey, e);
            throw new BadCredentialsException("Invalid API credentials");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
