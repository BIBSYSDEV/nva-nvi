package no.sikt.nva.nvi.events.batch.model;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.YearQueryRequest;
import no.sikt.nva.nvi.common.model.ListingResult;

public record LoadWorkItemsByYearRequest(
    BatchJobType jobType, List<String> years, PaginationStateV2 paginationState) {

  public YearQueryRequest getScanRequest() {
    return new YearQueryRequest(
        years.getFirst(), paginationState().batchSize(), paginationState.lastCandidateRead());
  }

  public LoadWorkItemsByYearRequest getNextState(ListingResult<UUID> scanResult) {
    var updatedTotal = paginationState.itemsProcessed() + scanResult.getTotalItemCount();
    var updatedYears = scanResult.shouldContinueScan() ? years : years.subList(1, years.size());
    var updatedStartMarker = scanResult.shouldContinueScan() ? scanResult.getStartMarker() : null;
    var updatedPaginationState =
        new PaginationStateV2(
            updatedTotal,
            paginationState().maxBatchSize(),
            paginationState().maxItems(),
            updatedStartMarker);
    return new LoadWorkItemsByYearRequest(jobType, updatedYears, updatedPaginationState);
  }
}
