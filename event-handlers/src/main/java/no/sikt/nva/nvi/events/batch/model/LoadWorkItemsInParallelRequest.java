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
        paginationState.getBatchSize(),
        paginationState.lastCandidateRead());
  }

  public LoadWorkItemsInParallelRequest getNextState(ListingResult<UUID> scanResult) {
    // TODO: Clean up
    var updatedTotal = paginationState.candidatesReadTotal() + scanResult.getTotalItemCount();
    var updatedStartMarker = scanResult.shouldContinueScan() ? scanResult.getStartMarker() : null;
    var updatedPaginationState =
        new PaginationStateV2(
            updatedTotal,
            paginationState().maxBatchSize(),
            paginationState().maxCandidatesToRead(),
            updatedStartMarker);
    return new LoadWorkItemsInParallelRequest(
        jobType, segment, totalSegments, updatedPaginationState);
  }
}
