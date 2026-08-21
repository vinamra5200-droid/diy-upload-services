package in.qualtechedge.qcp.templates.utils;

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
}
