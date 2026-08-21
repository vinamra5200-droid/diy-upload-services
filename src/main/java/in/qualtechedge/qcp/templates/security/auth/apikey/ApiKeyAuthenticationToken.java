package in.qualtechedge.qcp.templates.security.auth.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security authentication token for API key / API secret based authentication.
 * <p>
 * The {@code principal} is set to the authenticated API client object after successful
 * authentication. Before authentication, it holds the raw API key string.
 * The {@code credentials} holds the API secret.
 * <p>
 * The {@code apiType} field distinguishes between admin clients ({@code "ADMIN"}) and
 * tenant-scoped clients ({@code "TENANT"}).
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    public static final String API_KEY_TYPE_HEADER = "X-API-Type";
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_SECRET_HEADER = "X-API-Secret";

    public static final String TYPE_TENANT = "TENANT";
    public static final String TYPE_ADMIN = "ADMIN";

    private final String apiType;
    private final Object principal;
    private final String credentials;

    /**
     * Pre-authentication constructor — used when the filter first reads the headers.
     */
    public ApiKeyAuthenticationToken(String apiType, String apiKey, String apiSecret) {
        super(List.of());
        this.apiType = apiType;
        this.principal = apiKey;
        this.credentials = apiSecret;
        setAuthenticated(false);
    }

    /**
     * Post-authentication constructor — used by {@link ApiKeyAuthenticationProvider} after
     * successfully verifying the credentials.
     */
    public ApiKeyAuthenticationToken(String apiType, Object principal, String credentials,
                                     Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiType = apiType;
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getApiType() {
        return apiType;
    }

    /**
     * Returns the raw API key — only valid before authentication.
     * After authentication, {@link #getPrincipal()} returns the verified API client object.
     */
    public String getApiKey() {
        return isAuthenticated() ? null : (String) principal;
    }
}
