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
  public YearQueryRequest toQueryRequest() {
    return new YearQueryRequest(
        getFilterYear(), paginationState().batchSize(), paginationState().lastCandidateRead());
  }

  @JsonIgnore
  public Optional<CandidatesByYearRequest> getNextRequest(ListingResult<UUID> scanResult) {
    var nextPage = paginationState.createUpdatedPaginationState(scanResult);

    if (nextPage.hasCapacity() && scanResult.shouldContinueScan()) {
      return Optional.of(new CandidatesByYearRequest(jobType, yearFilter, nextPage));
    }

    if (nextPage.hasCapacity() && yearFilter.hasMoreYears()) {
      return Optional.of(
          new CandidatesByYearRequest(jobType, yearFilter.getIncrementedFilter(), nextPage));
    }

    return Optional.empty();
  }

  private String getFilterYear() {
    return yearFilter.reportingYears().getFirst();
  }
}
