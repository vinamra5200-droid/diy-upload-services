package in.qualtechedge.qcp.templates.utils;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resolves the {@code actorId} of the currently authenticated maker/checker admin from the JWT
 * (admin-api-contract.md: "token payload must contain actorId and role"). Falls back to the
 * standard {@code sub} claim so a token minted without a custom {@code actorId} claim still works.
 */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal in the current security context");
        }
        String actorId = jwt.getClaimAsString("actorId");
        return actorId != null ? actorId : jwt.getSubject();
    }

    /** Whether the current actor holds the given Spring Security role (without the {@code ROLE_} prefix). */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    /** Every role that can see across every maker's data on the viewer dashboard — the read-only
     * {@code viewer} role, plus {@code makerAdmin}/{@code checkerAdmin} (who can also drive the
     * admin-only retry/reject-fail overrides there). Centralizes the "who bypasses per-resource
     * ownership checks" answer so it's asked the same way everywhere instead of drifting. */
    private static final List<String> CROSS_ACTOR_READ_ROLES =
            List.of("viewer", "makerAdmin", "checkerAdmin");

    public static boolean hasCrossActorReadAccess() {
        return CROSS_ACTOR_READ_ROLES.stream().anyMatch(CurrentActor::hasRole);
    }
}
