package in.qualtechedge.qcp.templates.dto.response;

/**
 * One process's aggregate rows-actually-processed totals — backs the viewer dashboard's
 * per-process chart. Restricted to jobs that reached a terminal processing state (COMPLETED or
 * FAILED, i.e. dispatch was attempted); a still-QUEUED/PROCESSING job contributes nothing yet.
 * {@code totalRecords}/{@code passedRecords}/{@code failedRecords} are all sums of each job's
 * {@code passedRecords} (rows that passed validation and were actually dispatched) — not
 * {@code UploadJob.totalRecords}, which would also count rows that failed validation and
 * therefore were never sent for processing at all.
 *
 * @param totalRecords rows dispatched across every completed-or-failed job for this process
 * @param passedRecords rows dispatched in jobs that COMPLETED successfully
 * @param failedRecords rows dispatched in jobs that ultimately FAILED
 * @param jobCount number of completed-or-failed jobs summed (excludes still-in-flight jobs)
 */
public record ProcessRecordsSummaryResponse(
        String processId,
        String processName,
        long totalRecords,
        long passedRecords,
        long failedRecords,
        long jobCount) {
}
