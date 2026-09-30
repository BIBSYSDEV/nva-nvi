package no.sikt.nva.nvi.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ExpandedDocumentTool {

  private static final String ENTITY_DESCRIPTION_NODE = "entityDescription";
  private static final String CONTRIBUTORS_PREVIEW_NODE = "contributorsPreview";

  private ExpandedDocumentTool() {}

  /**
   * Removes the contributors preview from the body of an expanded publication. The preview repeats
   * some of the contributors as separate nodes, which would otherwise be validated twice.
   */
  public static JsonNode prepareJsonNodeForModel(JsonNode node) {
    if (node.get(ENTITY_DESCRIPTION_NODE) instanceof ObjectNode entityDescription) {
      entityDescription.remove(CONTRIBUTORS_PREVIEW_NODE);
    }
    return node;
  }
}
