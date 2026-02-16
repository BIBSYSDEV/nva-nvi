package no.sikt.nva.nvi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ExpandedDocumentTool {

  private static final String CONTRIBUTORS_PREVIEW_NODE = "contributorsPreview";

  private ExpandedDocumentTool() {}

  public static JsonNode prepareJsonNodeForModel(JsonNode node) {
    return node instanceof ObjectNode objectNode
        ? toNodeWithoutContributorsPreview(objectNode)
        : node;
  }

  private static JsonNode toNodeWithoutContributorsPreview(ObjectNode objectNode) {
    objectNode.remove(CONTRIBUTORS_PREVIEW_NODE);
    return objectNode;
  }
}
