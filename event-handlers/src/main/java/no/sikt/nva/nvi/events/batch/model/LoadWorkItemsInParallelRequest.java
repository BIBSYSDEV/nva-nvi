package no.sikt.nva.nvi.events.batch.model;

import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.TableScanRequest;
import no.sikt.nva.nvi.common.model.ListingResult;

public record LoadWorkItemsInParallelRequest(
    BatchJobType jobType, int segment, int totalSegments, PaginationStateV2 paginationState) {

  public TableScanRequest getScanRequest() {
    return new TableScanRequest(
        segment,
        totalSegments,
        paginationState.batchSize(),
        paginationState.lastCandidateRead());
  }

  public LoadWorkItemsInParallelRequest getNextState(ListingResult<UUID> scanResult) {
    // TODO: Clean up
    var updatedTotal = paginationState.itemsProcessed() + scanResult.getTotalItemCount();
    var updatedStartMarker = scanResult.shouldContinueScan() ? scanResult.getStartMarker() : null;
    var updatedPaginationState =
        new PaginationStateV2(
            updatedTotal,
            paginationState().maxBatchSize(),
            paginationState().maxItems(),
            updatedStartMarker);
    return new LoadWorkItemsInParallelRequest(
        jobType, segment, totalSegments, updatedPaginationState);
  }
}
