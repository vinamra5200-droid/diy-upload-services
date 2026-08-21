package in.qualtechedge.qcp.templates.utils;

/** Masks sensitive credential values before they leave the service layer in an API response. */
public final class SecretMasking {

    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final String MASK_PREFIX = "••••••••";

    private SecretMasking() {
    }

    /** Returns {@code null} for a blank secret, otherwise a fixed-length mask plus the last few characters. */
    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        int visible = Math.min(VISIBLE_SUFFIX_LENGTH, secret.length());
        return MASK_PREFIX + secret.substring(secret.length() - visible);
    }
}
