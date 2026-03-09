package no.sikt.nva.nvi.events.batch.request;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.validateValueIsNonZeroPositiveNumberIfSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.ListingResult;

public record PaginationState(
    int itemsProcessed, int maxBatchSize, Integer maxItems, Map<String, String> lastCandidateRead) {

  public PaginationState {
    validateValueIsNonZeroPositiveNumberIfSet(maxBatchSize);
    validateValueIsNonZeroPositiveNumberIfSet(maxItems);
  }

  public static PaginationState createInitialPaginationState(int maxBatchSize, Integer maxItems) {
    return new PaginationState(0, maxBatchSize, maxItems, null);
  }

  public PaginationState createUpdatedPaginationState(ListingResult<UUID> scanResult) {
    var updatedTotal = itemsProcessed + scanResult.itemCount();
    var updatedStartMarker = scanResult.hasNextPage() ? scanResult.lastEvaluatedKey() : null;
    return new PaginationState(updatedTotal, maxBatchSize, maxItems, updatedStartMarker);
  }

  @JsonIgnore
  public boolean hasCapacity() {
    return batchSize() > 0;
  }

  @JsonIgnore
  public boolean hasItemLimit() {
    return nonNull(maxItems);
  }

  @JsonIgnore
  public int batchSize() {
    return hasItemLimit() ? Integer.min(maxItems - itemsProcessed, maxBatchSize) : maxBatchSize;
  }
}
