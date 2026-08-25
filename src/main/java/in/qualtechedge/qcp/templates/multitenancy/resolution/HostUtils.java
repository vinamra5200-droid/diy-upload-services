package in.qualtechedge.qcp.templates.multitenancy.resolution;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;

/**
 * Extracts the tenant subdomain from the request {@code Host} header.
 * <p>
 * QCP subdomain convention (QCC Multi-Tenancy §2.1): {@code {tenant}-{product}-{env}.qualtechedge.in}
 * <ul>
 *   <li>{@code client1-app-dev.qualtechedge.in} → tenant {@code client1}</li>
 *   <li>{@code admin-app-dev.qualtechedge.in}   → {@code null} (superadmin console — system scope)</li>
 *   <li>{@code localhost} / bare domains        → {@code null} (no tenant)</li>
 * </ul>
 */
public final class HostUtils {

    /** The console subdomain. Deliberately not a tenant: no registry row, no pool. */
    public static final String ADMIN_SUBDOMAIN = "admin";

    private HostUtils() {
    }

    /**
     * The host this request was originally addressed to, preferring {@code X-Forwarded-Host}
     * over {@code Host}.
     * <p>
     * Behind a proxy the two differ, and only the forwarded one is the browser's. That is not
     * merely a nicety: Node's {@code fetch} refuses to let a caller set {@code Host}, so a Next
     * front end calling this service cannot pass the browser's host on that header however it
     * asks — the request arrives claiming {@code localhost:<port>}, and with it every tenant
     * looks like the console. {@code X-Forwarded-Host} is the one that survives.
     * <p>
     * Trusted because the edge is required to overwrite it — the front-end templates say the
     * same. A deployment that lets a client set it through to here has a proxy problem, not a
     * resolution problem.
     */
    public static String fromRequest(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Host");
        if (forwarded != null && !forwarded.isBlank()) {
            // A chain of proxies appends; the first entry is the browser's.
            return forwarded.split(",")[0].trim();
        }
        return request.getHeader("Host");
    }

    /**
     * Resolve a {@code Host} header to the scope it addresses, checking every part of the
     * convention rather than only the tenant code.
     * <p>
     * {@link #extractSubdomain(String)} reads the first {@code -} separated segment and stops.
     * That accepts {@code qc-kyc-dev} on a service that serves {@code ibs} — a different
     * product's host resolving to a real tenant here — and {@code qc-ibs-prod} on a dev
     * instance. Anything not matching the shape at all falls through to {@code null}, which
     * callers read as system scope, so an unrecognised host quietly gets the admin plane.
     * <p>
     * This returns three outcomes instead: system, a tenant, or an explicit refusal.
     *
     * @param hostHeader  raw {@code Host} header
     * @param product     the product segment this deployment answers to, e.g. {@code ibs}
     * @param environment the environment segment, e.g. {@code dev} — normally the active profile
     * @param baseDomain  the domain the subdomains sit under, e.g. {@code qualtechedge.in};
     *                    blank disables the check
     * @param systemHosts hosts served in system scope without a subdomain, e.g. {@code localhost}
     */
    public static HostScope resolve(String hostHeader, String product, String environment,
            String baseDomain, List<String> systemHosts) {
        String domain = extractDomain(hostHeader);
        if (domain == null || domain.isBlank()) {
            return HostScope.invalid("no Host header");
        }
        domain = domain.toLowerCase(Locale.ROOT);

        if (systemHosts != null && systemHosts.stream().anyMatch(domain::equalsIgnoreCase)) {
            return HostScope.system();
        }

        // Local development: {tenant}.localhost — same carve-out as extractSubdomain() below.
        // The production {tenant}-{product}-{env}.{base-domain} convention doesn't apply locally
        // (no product/env segments), so match it before the base-domain/subdomain-shape checks
        // rather than requiring QCP_HOST_BASE_DOMAIN to be repointed at "localhost" for dev.
        int firstDot = domain.indexOf('.');
        if (firstDot > 0 && domain.substring(firstDot + 1).equals("localhost")) {
            String localCode = domain.substring(0, firstDot);
            return ADMIN_SUBDOMAIN.equals(localCode) ? HostScope.system() : HostScope.tenant(localCode);
        }

        String expectedSuffix = baseDomain == null ? "" : baseDomain.trim().toLowerCase(Locale.ROOT);
        String subdomain;
        if (expectedSuffix.isEmpty()) {
            // No base domain configured: the subdomain is everything before the first dot.
            int dot = domain.indexOf('.');
            if (dot <= 0) {
                return HostScope.invalid("host carries no subdomain");
            }
            subdomain = domain.substring(0, dot);
        } else {
            if (!domain.endsWith("." + expectedSuffix)) {
                return HostScope.invalid("host is not under " + expectedSuffix);
            }
            subdomain = domain.substring(0, domain.length() - expectedSuffix.length() - 1);
            // Exactly one label of subdomain: qc-app-dev, never qc-app-dev.internal
            if (subdomain.contains(".")) {
                return HostScope.invalid("host has more subdomain labels than the convention allows");
            }
        }

        String[] parts = subdomain.split("-");
        if (parts.length != 3) {
            return HostScope.invalid("subdomain is not {tenant}-{product}-{env}");
        }
        if (product != null && !product.isBlank() && !parts[1].equalsIgnoreCase(product)) {
            return HostScope.invalid("host is for product '" + parts[1] + "', this service serves '" + product + "'");
        }
        if (environment != null && !environment.isBlank() && !parts[2].equalsIgnoreCase(environment)) {
            return HostScope.invalid(
                    "host is for environment '" + parts[2] + "', this instance is '" + environment + "'");
        }

        String code = parts[0].toLowerCase(Locale.ROOT);
        return ADMIN_SUBDOMAIN.equals(code) ? HostScope.system() : HostScope.tenant(code);
    }

    /** Returns the host part of the header without port or path (e.g. {@code client1-app-dev.qualtechedge.in}). */
    public static String extractDomain(String hostHeader) {
        if (hostHeader == null || hostHeader.isEmpty()) {
            return null;
        }
        String withoutProtocol = hostHeader.contains("://") ? hostHeader.split("://")[1] : hostHeader;
        String domain = withoutProtocol.contains("/") ? withoutProtocol.split("/")[0] : withoutProtocol;
        return domain.contains(":") ? domain.split(":")[0] : domain;
    }

    /**
     * Returns the tenant short code from the subdomain, or {@code null} when the host carries no
     * tenant (bare domain, localhost, or the {@code admin-*} superadmin subdomain).
     */
    public static String extractSubdomain(String hostHeader) {
        String domain = extractDomain(hostHeader);
        if (domain == null) {
            return null;
        }

        String[] labels = domain.split("\\.");

        // Local development: {tenant}.localhost → return tenant
        if (labels.length == 2 && "localhost".equalsIgnoreCase(labels[1])) {
            return labels[0];
        }

        // {subdomain}.{domain}.{tld} — standard production format
        if (labels.length != 3) {
            return null;
        }

        String subdomain = labels[0];
        if (subdomain.startsWith("admin-")) {
            return null; // superadmin console — system scope, not a tenant
        }

        // {tenant}-{product}-{env} → tenant is the first '-' separated segment
        return subdomain.split("-")[0];
    }
}
