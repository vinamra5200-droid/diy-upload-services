package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.ConsumerCallbackBatchesResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps a job's {@code callback_batch_attempts} (one row per Kafka chunk) onto the contiguous
 * source-row spans they cover, in dispatch order — there is no per-row delivery result, since a
 * chunk is posted to the third party as one atomic HTTP call, so every original row within it
 * shares that chunk's outcome and API response. Shared by every consumer that needs to turn
 * batch-level delivery detail back into row-level detail: {@link UploadJobServiceImpl#getJobRows}
 * (the maker's on-demand rows table) and {@link ProcessedResultS3Exporter} (the downloadable
 * processed-file export).
 */
final class CallbackRowRanges {

    private CallbackRowRanges() {
    }

    /** One Kafka chunk's contiguous source-row span, carrying the single outcome/API response every
     * row in it shares. */
    record RowRange(int startRow, int endRow, String status, Integer httpStatusCode, String responseText) {
        long size() {
            return (long) endRow - startRow + 1;
        }
    }

    /**
     * Batches sorted by {@code chunkSequence} (dispatch order), each contributing
     * {@code rowCount} rows starting right after the previous one — a batch with a null/zero
     * {@code rowCount} (the trailing empty {@code lastChunk} marker) contributes no span at all.
     */
    static List<RowRange> build(List<ConsumerCallbackBatchesResponse.Batch> batches) {
        List<ConsumerCallbackBatchesResponse.Batch> sorted = new ArrayList<>(batches);
        sorted.sort(Comparator.comparing(ConsumerCallbackBatchesResponse.Batch::chunkSequence));

        List<RowRange> ranges = new ArrayList<>();
        int nextRow = 1;
        for (ConsumerCallbackBatchesResponse.Batch batch : sorted) {
            int rowCount = batch.rowCount() == null ? 0 : batch.rowCount();
            if (rowCount <= 0) {
                continue;
            }
            String status = "SUCCESS".equals(batch.outcome()) ? "PASSED" : "FAILED";
            String responseText = batch.responseBody() != null ? batch.responseBody() : batch.errorMessage();
            ranges.add(new RowRange(nextRow, nextRow + rowCount - 1, status, batch.httpStatusCode(), responseText));
            nextRow += rowCount;
        }
        return ranges;
    }

    /** {@code null} if {@code rowNumber} falls outside every range — shouldn't happen for a row
     * that actually came from the same dispatched file these ranges were built from. */
    static RowRange find(List<RowRange> ranges, int rowNumber) {
        for (RowRange range : ranges) {
            if (rowNumber >= range.startRow() && rowNumber <= range.endRow()) {
                return range;
            }
        }
        return null;
    }
}
