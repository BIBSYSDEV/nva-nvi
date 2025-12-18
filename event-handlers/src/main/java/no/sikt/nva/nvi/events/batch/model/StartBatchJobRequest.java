package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record StartBatchJobRequest(
    BatchJobType jobType,
    BatchJobFilter filter,
    Integer maxItemsPerSegment,
    Integer maxParallelSegments,
    PaginationState paginationState)
    implements JsonSerializable {

  public static final int DEFAULT_PARALLEL_SEGMENTS = 10;

  public StartBatchJobRequest {
    requireNonNull(jobType, "jobType must not be null");
    filter = requireNonNullElse(filter, new ReportingYearFilter(emptyList()));
    maxParallelSegments = requireNonNullElse(maxParallelSegments, DEFAULT_PARALLEL_SEGMENTS);
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
    return filter instanceof ReportingYearFilter yearFilter && !yearFilter.includesAllYears();
  }

  public StartBatchJobRequest withPaginationState(PaginationState newPaginationState) {
    return new StartBatchJobRequest(
        jobType, filter, maxItemsPerSegment, maxParallelSegments, newPaginationState);
  }
}
