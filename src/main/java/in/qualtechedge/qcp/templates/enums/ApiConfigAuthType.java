package in.qualtechedge.qcp.templates.enums;

/** Authentication scheme carried by an outbound API config's {@code auth} JSON blob. */
public enum ApiConfigAuthType {
    none,
    basic,
    bearer,
    apiKey
}
