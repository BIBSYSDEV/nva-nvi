package no.sikt.nva.nvi.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class JsonUtils {
  private static final CharSequence JSON_PATH_DELIMITER = ".";

  private JsonUtils() {}

  public static String extractJsonNodeTextValue(JsonNode node, String jsonPointer) {
    return Optional.ofNullable(node.at(jsonPointer))
        .filter(JsonUtils::isNotMissingNode)
        .map(JsonNode::textValue)
        .orElse(null);
  }

  public static Optional<String> extractOptJsonNodeTextValue(JsonNode node, String jsonPointer) {
    return Optional.ofNullable(node.at(jsonPointer))
        .filter(JsonUtils::isNotMissingNode)
        .map(JsonNode::textValue);
  }

  public static Stream<JsonNode> streamNode(JsonNode node) {
    return StreamSupport.stream(node.spliterator(), false);
  }

  public static String jsonPathOf(String... args) {
    return String.join(JSON_PATH_DELIMITER, args);
  }

  private static boolean isNotMissingNode(JsonNode node) {
    return !node.isMissingNode() && !node.isNull() && node.isValueNode();
  }
}
