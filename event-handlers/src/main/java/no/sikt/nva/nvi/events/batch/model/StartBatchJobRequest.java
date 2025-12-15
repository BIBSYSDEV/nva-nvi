package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import no.unit.nva.commons.json.JsonSerializable;

public record StartBatchJobRequest(
    BatchJobType jobType,
    ReportingYearFilter filter,
    Integer maxItemsPerSegment,
    Integer parallelSegments,
    PaginationState paginationState)
    implements JsonSerializable {

  public static final int DEFAULT_PARALLEL_SEGMENTS = 10;

  public StartBatchJobRequest {
    requireNonNull(jobType, "jobType must not be null");
    filter = requireNonNullElse(filter, new ReportingYearFilter(emptyList()));
    parallelSegments = requireNonNullElse(parallelSegments, DEFAULT_PARALLEL_SEGMENTS);
  }

  @JsonIgnore
  public boolean hasItemLimit() {
    return nonNull(maxItemsPerSegment);
  }

  @JsonIgnore
  public boolean isInitialInvocation() {
    return isNull(paginationState);
  }

  @JsonIgnore
  public boolean hasYearFilter() {
    return !filter.includesAllYears();
  }

  public StartBatchJobRequest withPaginationState(PaginationState newPaginationState) {
    return new StartBatchJobRequest(
        jobType, filter, maxItemsPerSegment, parallelSegments, newPaginationState);
  }
}
