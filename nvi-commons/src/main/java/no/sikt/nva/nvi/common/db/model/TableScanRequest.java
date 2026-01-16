package no.sikt.nva.nvi.common.db.model;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Pagination parameters for a table scan request that can be done in parallel segments.
 *
 * @param segment Numeric ID of this segment
 * @param totalSegments Total number of segments
 * @param batchSize Number of records to read in next scan
 * @param lastItemRead Key of last item read (null for first request)
 */
public record TableScanRequest(
    int segment, int totalSegments, int batchSize, Map<String, String> lastItemRead) {

  // FIXME
  public TableScanRequest next(Map<String, String> lastItemRead, int batchSize) {
    return new TableScanRequest(segment, totalSegments, batchSize, lastItemRead);
  }

  public static TableScanRequest initial(int segment, int totalSegments, int batchSize) {
    return new TableScanRequest(segment, totalSegments, batchSize, null);
  }

  public Map<String, AttributeValue> exclusiveStartKey() {
    return nonNull(lastItemRead) ? toAttributeMap(lastItemRead) : null;
  }

  private static Map<String, AttributeValue> toAttributeMap(Map<String, String> startMarker) {
    return startMarker.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> AttributeValue.builder().s(e.getValue()).build()));
  }
}
