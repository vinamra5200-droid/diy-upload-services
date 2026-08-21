package in.qualtechedge.qcp.templates.multitenancy.resolution;

/**
 * What a request's {@code Host} header resolves to.
 * <p>
 * Deliberately three outcomes, not a nullable tenant code. "No tenant" and "host I do not
 * recognise" are different things: the first is the console, the second is a misconfiguration or
 * a probe, and collapsing them means an unrecognised host silently gets admin scope.
 *
 * @param kind       system (console), tenant, or unrecognised
 * @param tenantCode lowercase short code; only set for {@link Kind#TENANT}
 * @param reason     why an {@link Kind#INVALID} host was refused — for the log, not the response
 */
public record HostScope(Kind kind, String tenantCode, String reason) {

    public enum Kind {
        /** The console / admin scope: the system database, no tenant. */
        SYSTEM,
        /** A tenant subdomain that parses; whether it exists is the registry's business. */
        TENANT,
        /** Not a host this service serves. */
        INVALID
    }

    public static HostScope system() {
        return new HostScope(Kind.SYSTEM, null, null);
    }

    public static HostScope tenant(String code) {
        return new HostScope(Kind.TENANT, code, null);
    }

    public static HostScope invalid(String reason) {
        return new HostScope(Kind.INVALID, null, reason);
    }

    public boolean isTenant() {
        return kind == Kind.TENANT;
    }

    public boolean isInvalid() {
        return kind == Kind.INVALID;
    }
}
