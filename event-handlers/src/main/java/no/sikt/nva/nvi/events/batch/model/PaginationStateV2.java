package no.sikt.nva.nvi.events.batch.model;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.validateValueIsNonZeroPositiveNumberIfSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

public record PaginationStateV2(
    int candidatesReadTotal,
    int maxBatchSize,
    Integer maxCandidatesToRead,
    Map<String, String> lastCandidateRead) {

  public PaginationStateV2 {
    validateValueIsNonZeroPositiveNumberIfSet(candidatesReadTotal);
    validateValueIsNonZeroPositiveNumberIfSet(maxBatchSize);
    validateValueIsNonZeroPositiveNumberIfSet(maxCandidatesToRead);
  }

  @JsonIgnore
  public boolean hasItemLimit() {
    return nonNull(maxCandidatesToRead);
  }

  @JsonIgnore
  public int getBatchSize() {
    return hasItemLimit() ? Integer.min(maxCandidatesToRead(), maxBatchSize) : maxBatchSize;
  }
}
