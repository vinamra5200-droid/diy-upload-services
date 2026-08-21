package in.qualtechedge.qcp.templates.exception;

/**
 * Raised when a process/template mutation is attempted while {@code config_locked = true}
 * (HTTP 423) — an active upload is holding the config (admin-api-contract.md §12.5).
 */
public class ConfigLockedException extends RuntimeException {

    public ConfigLockedException(String message) {
        super(message);
    }
}
