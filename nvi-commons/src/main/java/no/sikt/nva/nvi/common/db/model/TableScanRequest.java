package no.sikt.nva.nvi.common.db.model;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record TableScanRequest(
    int segment, int totalSegments, int batchSize, Map<String, String> lastItemRead) {

  public Map<String, AttributeValue> exclusiveStartKey() {
    return nonNull(lastItemRead) ? toAttributeMap(lastItemRead) : null;
  }

  private static Map<String, AttributeValue> toAttributeMap(Map<String, String> startMarker) {
    return startMarker.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> AttributeValue.builder().s(e.getValue()).build()));
  }
}
