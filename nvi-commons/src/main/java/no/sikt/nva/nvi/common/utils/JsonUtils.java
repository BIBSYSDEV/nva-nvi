package no.sikt.nva.nvi.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

public class JsonUtils {

    public static String extractJsonNodeTextValue(JsonNode node, String jsonPointer) {
        return Optional.ofNullable(node.at(jsonPointer))
                   .filter(JsonUtils::isNotMissingNode)
                   .map(JsonNode::textValue)
                   .orElse(null);
    }

    private static boolean isNotMissingNode(JsonNode node) {
        return !node.isMissingNode() && !node.isNull() && node.isValueNode();
    }

}
