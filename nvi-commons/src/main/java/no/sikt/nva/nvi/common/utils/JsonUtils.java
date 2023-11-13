package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
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

    public static Stream<JsonNode> streamNode(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    public static URI extractId(JsonNode jsonNode) {
        return URI.create(extractJsonNodeTextValue(jsonNode, JSON_PTR_ID));
    }

    private static boolean isNotMissingNode(JsonNode node) {
        return !node.isMissingNode() && !node.isNull() && node.isValueNode();
    }
}
