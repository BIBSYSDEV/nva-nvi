package no.sikt.nva.nvi.events.batch.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.YearQueryRequest;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;

public record CandidatesByYearRequest(
    BatchJobType jobType, ReportingYearFilter yearFilter, PaginationState paginationState)
    implements BatchJobRequest {

  @JsonIgnore
  public int batchSize() {
    return paginationState().batchSize();
  }

  @JsonIgnore
  public YearQueryRequest toQueryRequest() {
    return new YearQueryRequest(
        getFilterYear(), batchSize(), paginationState().lastCandidateRead());
  }

  @JsonIgnore
  public Optional<CandidatesByYearRequest> getNextRequest(ListingResult<UUID> scanResult) {
    // FIXME: Clean up conditional logic
    // Continue if
    // maxItems isn't reached AND
    // scanResult has more OR yearFilter has more
    var updatedPaginationState = paginationState.createUpdatedPaginationState(scanResult);
    var canContinueCurrentYear =
        scanResult.shouldContinueScan() && updatedPaginationState.batchSize() > 0;
    var canContinueNextYear = yearFilter.hasMoreYears() && updatedPaginationState.batchSize() > 0;

    if (canContinueCurrentYear) {
      return Optional.of(new CandidatesByYearRequest(jobType, yearFilter, updatedPaginationState));
    } else if (canContinueNextYear) {
      return Optional.of(
          new CandidatesByYearRequest(
              jobType, yearFilter.getIncrementedFilter(), updatedPaginationState));
    } else {
      return Optional.empty();
    }
  }

  private String getFilterYear() {
    return yearFilter.reportingYears().getFirst();
  }
}
