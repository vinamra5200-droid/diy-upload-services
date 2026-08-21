package in.qualtechedge.qcp.templates.dto.response;

/**
 * What a browser needs to start an OpenID Connect login against this deployment.
 *
 * <p>Discovered rather than configured. A single-page app that hardcodes its issuer and client id
 * has to be rebuilt per environment, and per tenant once one realm serves several — so the same
 * bundle would sign a tenant into the wrong realm, or name a client that is not in theirs. The
 * service already resolves both from the request host; this hands the answer over.
 *
 * <p>Every field is public information: these are the values the browser is about to put in a
 * redirect URL. There is no secret here, and there must not be — the console is a public client.
 *
 * @param issuer                the realm's issuer URL
 * @param authorizationEndpoint where to send the browser to sign in
 * @param tokenEndpoint         where the browser exchanges its authorization code
 * @param endSessionEndpoint    where to send the browser to sign out
 * @param clientId              the client this host's console authenticates as
 * @param configured            false when Keycloak is not wired up, so the front end can say so
 *                              instead of redirecting into a broken flow
 */
public record AuthConfigResponse(
        String issuer,
        String authorizationEndpoint,
        String tokenEndpoint,
        String endSessionEndpoint,
        String clientId,
        boolean configured) {
}
