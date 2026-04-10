package no.sikt.nva.nvi.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class JsonUtils {
  private static final String JSON_PATH_DELIMITER = ".";

  private JsonUtils() {}

  public static String extractJsonNodeTextValue(JsonNode node, String jsonPointer) {
    return Optional.ofNullable(node.at(jsonPointer))
        .filter(JsonUtils::isNotMissingNode)
        .map(JsonUtils::extractTextNode)
        .orElse(null);
  }

  private static String extractTextNode(JsonNode node) {
    if (node.isArray() && !node.isEmpty()) {
      return extractTextNode(node.get(0));
    } else if (node.isTextual() || node.isNull()) {
      return node.textValue();
    }
    throw new IllegalArgumentException(
        "Text value was not a string array or text value: %s".formatted(node));
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
    return !node.isMissingNode() && !node.isNull() && isSupportedNodeType(node);
  }

  private static boolean isSupportedNodeType(JsonNode node) {
    return node.isValueNode() || node.isArray();
  }
}
