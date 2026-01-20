package no.sikt.nva.nvi.common.db.request;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public interface CandidateScanRequest {
  Map<String, String> lastItemRead();

  default Map<String, AttributeValue> exclusiveStartKey() {
    return Optional.ofNullable(lastItemRead())
        .map(CandidateScanRequest::toAttributeMap)
        .orElse(null);
  }

  private static Map<String, AttributeValue> toAttributeMap(Map<String, String> startMarker) {
    return startMarker.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> AttributeValue.builder().s(e.getValue()).build()));
  }
}
