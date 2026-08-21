package in.qualtechedge.qcp.templates.constants;

/**
 * Keycloak realm defaults that are genuinely fixed across products.
 *
 * <p>Everything identifying — client id patterns and client secrets — moved to
 * {@code spring.keycloak.*} configuration. They were constants here, which meant the template's
 * own name and a working default secret were compiled into every project cloned from it: the
 * kind of value nobody changes precisely because nothing fails when they don't.
 */
public final class KeycloakConstant {

    private KeycloakConstant() {
        throw new IllegalStateException("Utility class");
    }

    /** Access token lifetime, in seconds, for realms this service creates. */
    public static final int ACCESS_TOKEN_LIFESPAN = 1800;

    /** SSO session idle timeout, in seconds, for realms this service creates. */
    public static final int SSO_SESSION_TIMEOUT = 28800;
}
