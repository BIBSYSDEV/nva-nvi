package no.sikt.nva.nvi.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String extractJsonNodeTextValue(JsonNode node, String jsonPointer) {
        return Optional.ofNullable(node.at(jsonPointer))
                   .filter(JsonUtils::isNotMissingNode)
                   .map(JsonNode::textValue)
                   .orElse(null);
    }

    public static String extractJsonNodeAsString(JsonNode node, String jsonPointer) {
        return Optional.ofNullable(node.at(jsonPointer))
                   .map(JsonNode::toString)
                   .orElse(null);
    }

    public static Stream<JsonNode> streamNode(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    private static boolean isNotMissingNode(JsonNode node) {
        return !node.isMissingNode() && !node.isNull() && node.isValueNode();
    }

}
