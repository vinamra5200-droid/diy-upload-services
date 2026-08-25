package in.qualtechedge.qcp.templates.utils;

import java.util.UUID;

/**
 * Mirrors the SQL {@code generate_id(prefix)} default used throughout the DIY Upload schema
 * (e.g. {@code proc-a1b2c3d4}). IDs are generated here rather than left to the DB DEFAULT,
 * because Hibernate always includes the mapped {@code @Id} column in its INSERT statement — a
 * DB-side DEFAULT on that column would never fire.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String generate(String prefix) {
        String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + "-" + hex;
    }

    /**
     * Formats a value pulled from a DB sequence (e.g. {@code process_id_seq} via
     * {@code UploadProcessRepository#nextProcessIdSequence}) to match
     * {@code generate_sequential_id(prefix, seq_name)} (V1_3_2) — e.g. {@code proc-000001}. Used
     * for process_id/template_id, which need to sort in creation order; every other id keeps the
     * random {@link #generate(String)} form.
     */
    public static String fromSequence(String prefix, long sequenceValue) {
        return prefix + "-" + String.format("%06d", sequenceValue);
    }
}
