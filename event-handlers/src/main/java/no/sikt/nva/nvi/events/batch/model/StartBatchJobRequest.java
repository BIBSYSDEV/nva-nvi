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

  private static final int DEFAULT_PARALLEL_SEGMENTS = 10;

  public StartBatchJobRequest {
    requireNonNull(jobType, "jobType must not be null");
    filter = requireNonNullElse(filter, new ReportingYearFilter(emptyList()));
    maxParallelSegments = requireNonNullElse(maxParallelSegments, DEFAULT_PARALLEL_SEGMENTS);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder copy() {
    return builder()
        .withJobType(jobType)
        .withFilter(filter)
        .withMaxItemsPerSegment(maxItemsPerSegment)
        .withMaxParallelSegments(maxParallelSegments)
        .withPaginationState(paginationState);
  }

  @JsonIgnore
  public boolean hasItemLimit() {
    return nonNull(maxItemsPerSegment);
  }

  @JsonIgnore
  public int maxRemainingItems() {
    if (hasItemLimit()) {
      return maxItemsPerSegment() - paginationState.itemsEnqueued();
    }
    return Integer.MAX_VALUE;
  }

  @JsonIgnore
  public boolean isInitialInvocation() {
    return isNull(paginationState);
  }

  @JsonIgnore
  public boolean hasYearFilter() {
    return filter instanceof ReportingYearFilter yearFilter && !yearFilter.includesAllYears();
  }

  public static final class Builder {
    private BatchJobType jobType;
    private BatchJobFilter filter;
    private Integer maxItemsPerSegment;
    private Integer maxParallelSegments;
    private PaginationState paginationState;

    private Builder() {}

    public Builder withJobType(BatchJobType jobType) {
      this.jobType = jobType;
      return this;
    }

    public Builder withFilter(BatchJobFilter filter) {
      this.filter = filter;
      return this;
    }

    public Builder withMaxItemsPerSegment(Integer maxItemsPerSegment) {
      this.maxItemsPerSegment = maxItemsPerSegment;
      return this;
    }

    public Builder withMaxParallelSegments(Integer maxParallelSegments) {
      this.maxParallelSegments = maxParallelSegments;
      return this;
    }

    public Builder withPaginationState(PaginationState paginationState) {
      this.paginationState = paginationState;
      return this;
    }

    public StartBatchJobRequest build() {
      return new StartBatchJobRequest(
          jobType, filter, maxItemsPerSegment, maxParallelSegments, paginationState);
    }
  }
}
