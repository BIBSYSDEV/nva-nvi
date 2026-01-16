package no.sikt.nva.nvi.events.batch.model;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.validateValueIsNonZeroPositiveNumberIfSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

public record PaginationStateV2(
    int itemsProcessed,
    int maxBatchSize,
    Integer maxItems,
    Map<String, String> lastCandidateRead) {

  public PaginationStateV2 {
    validateValueIsNonZeroPositiveNumberIfSet(itemsProcessed);
    validateValueIsNonZeroPositiveNumberIfSet(maxBatchSize);
    validateValueIsNonZeroPositiveNumberIfSet(maxItems);
  }

  @JsonIgnore
  public boolean hasItemLimit() {
    return nonNull(maxItems);
  }

  @JsonIgnore
  public int batchSize() {
    return hasItemLimit() ? Integer.min(maxItems(), maxBatchSize) : maxBatchSize;
  }
}
