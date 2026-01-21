package no.sikt.nva.nvi.events.batch.request;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static no.sikt.nva.nvi.common.utils.Validator.validateValueIsNonZeroPositiveNumberIfSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import no.sikt.nva.nvi.events.batch.model.BatchJobFilter;

public record StartBatchJobRequest(
    BatchJobType jobType,
    BatchJobFilter filter,
    Integer maxBatchSize,
    Integer maxItems,
    Integer maxParallelSegments)
    implements BatchJobRequest {

  private static final int DEFAULT_PAGE_SIZE = 700;
  private static final int DEFAULT_PARALLEL_SEGMENTS = 10;

  public StartBatchJobRequest {
    validateValueIsNonZeroPositiveNumberIfSet(maxItems);
    validateValueIsNonZeroPositiveNumberIfSet(maxParallelSegments);
    requireNonNull(jobType, "jobType must not be null");
    maxParallelSegments = requireNonNullElse(maxParallelSegments, DEFAULT_PARALLEL_SEGMENTS);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder copy() {
    return builder()
        .withJobType(jobType)
        .withFilter(filter)
        .withMaxItems(maxItems)
        .withMaxParallelSegments(maxParallelSegments);
  }

  @JsonIgnore
  public boolean hasItemLimit() {
    return nonNull(maxItems);
  }

  @JsonIgnore
  public int batchSize() {
    return nonNull(maxBatchSize) ? Integer.min(maxBatchSize, DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
  }

  public static final class Builder {
    private BatchJobType jobType;
    private BatchJobFilter filter;
    private Integer maxBatchSize;
    private Integer maxItems;
    private Integer maxParallelSegments;

    private Builder() {}

    public Builder withJobType(BatchJobType jobType) {
      this.jobType = jobType;
      return this;
    }

    public Builder withFilter(BatchJobFilter filter) {
      this.filter = filter;
      return this;
    }

    public Builder withMaxItems(Integer maxItems) {
      this.maxItems = maxItems;
      return this;
    }

    public Builder withMaxBatchSize(Integer maxBatchSize) {
      this.maxBatchSize = maxBatchSize;
      return this;
    }

    public Builder withMaxParallelSegments(Integer maxParallelSegments) {
      this.maxParallelSegments = maxParallelSegments;
      return this;
    }

    public StartBatchJobRequest build() {
      return new StartBatchJobRequest(jobType, filter, maxBatchSize, maxItems, maxParallelSegments);
    }
  }
}
