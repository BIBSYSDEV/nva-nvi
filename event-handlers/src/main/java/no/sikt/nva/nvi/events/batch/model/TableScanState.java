package no.sikt.nva.nvi.events.batch.model;

import java.util.Map;
import javax.annotation.Nullable;

public record TableScanState(
    int segment,
    int totalSegments,
    @Nullable Map<String, String> lastEvaluatedKey,
    int itemsEnqueued)
    implements PaginationState {

  public static TableScanState forSegment(int segment, int totalSegments) {
    return new TableScanState(segment, totalSegments, null, 0);
  }

  @Override
  public TableScanState withNextPage(Map<String, String> newLastEvaluatedKey, int additionalItems) {
    return new TableScanState(
        segment, totalSegments, newLastEvaluatedKey, itemsEnqueued + additionalItems);
  }
}
