package no.sikt.nva.nvi.common.db.model;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// TODO: Javadoc
public record YearQueryRequest(String year, int batchSize, Map<String, String> lastItemRead) {

  // FIXME
  public YearQueryRequest next(Map<String, String> lastItemRead, int batchSize) {
    return new YearQueryRequest(year, batchSize, lastItemRead);
  }

  public static YearQueryRequest initial(String year, int batchSize) {
    return new YearQueryRequest(year, batchSize, null);
  }

  public Map<String, AttributeValue> exclusiveStartKey() {
    return nonNull(lastItemRead) ? toAttributeMap(lastItemRead) : null;
  }

  private static Map<String, AttributeValue> toAttributeMap(Map<String, String> startMarker) {
    return startMarker.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> AttributeValue.builder().s(e.getValue()).build()));
  }
}
