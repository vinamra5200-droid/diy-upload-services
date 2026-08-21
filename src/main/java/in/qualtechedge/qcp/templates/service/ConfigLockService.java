package in.qualtechedge.qcp.templates.service;

/**
 * Owns {@code config_locks} — one row per upload currently in flight for a process, so
 * template/process config can't be edited out from under it (see {@code ConfigLockedException},
 * raised by the maker-admin edit paths, not by this service). Multiple uploads can each hold
 * their own row for the same process at once: {@link #acquire} never rejects a concurrent
 * upload — only {@link #isLocked} matters, and it stays true for as long as any row exists.
 */
public interface ConfigLockService {

    /**
     * Adds a lock row for {@code processId} under {@code lockRef} (the {@code uploadId}, later
     * reassigned to the Kafka {@code batchId} once it exists — see {@link #reassignRef}).
     */
    void acquire(String processId, String lockRef);

    /** Moves the lock ref forward (uploadId -&gt; batchId) once the S3 PUT succeeds and a jobId exists. */
    void reassignRef(String processId, String oldRef, String newRef);

    /** Releases the lock row held under {@code lockRef}, if any. */
    void release(String lockRef);

    /** Whether any upload currently holds a lock on {@code processId} — blocks maker-admin config edits. */
    boolean isLocked(String processId);

    /** Force-releases any lock row held longer than {@code timeoutMinutes}. Returns how many were released. */
    int releaseStale(int timeoutMinutes);
}
