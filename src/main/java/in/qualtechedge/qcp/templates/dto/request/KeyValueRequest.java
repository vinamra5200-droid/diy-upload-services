package in.qualtechedge.qcp.templates.dto.request;

/** {@code {key, value}} pair used by API config query params and headers. Blank keys are dropped. */
public record KeyValueRequest(String key, String value) {
}
