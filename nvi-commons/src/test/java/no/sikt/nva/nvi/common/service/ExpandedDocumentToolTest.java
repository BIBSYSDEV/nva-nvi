package no.sikt.nva.nvi.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

class ExpandedDocumentToolTest {

  private static final String CONTRIBUTORS_PREVIEW_JSON_POINTER = "/contributorsPreview";

  @Test
  void shouldRemoveContributorsPreviewNodeFromJsonNodeWhenPresent() throws JsonProcessingException {
    var json =
        """
        {
          "contributorsPreview": [{}]
        }
        """;
    var node = JsonUtils.dtoObjectMapper.readTree(json);

    assertFalse(node.at(CONTRIBUTORS_PREVIEW_JSON_POINTER).isEmpty());

    var nodeWithoutContributorsPreview = ExpandedDocumentTool.prepareJsonNodeForModel(node);

    assertTrue(nodeWithoutContributorsPreview.at(CONTRIBUTORS_PREVIEW_JSON_POINTER).isEmpty());
  }

  @Test
  void shouldHandleJsonNodeWithoutContributorsPreview() throws JsonProcessingException {
    var json =
        """
        {
        }
        """;
    var node = JsonUtils.dtoObjectMapper.readTree(json);

    var nodeWithoutContributorsPreview = ExpandedDocumentTool.prepareJsonNodeForModel(node);

    assertTrue(nodeWithoutContributorsPreview.at(CONTRIBUTORS_PREVIEW_JSON_POINTER).isEmpty());
  }

  @Test
  void shouldNotDoOtherChangesToJsonNodeThanRemovingContributorsPreviewNode()
      throws JsonProcessingException {
    var json =
        """
            {
              "contributorsPreview": [{}],
              "otherPresentNode": {
              },
              "anotherNode": "Node"
            }
        """;
    var node = JsonUtils.dtoObjectMapper.readTree(json);

    var nodeWithoutContributorsPreview = ExpandedDocumentTool.prepareJsonNodeForModel(node);

    var expectedNode =
        """
            {
              "otherPresentNode": {
              },
              "anotherNode": "Node"
            }
        """;

    assertEquals(JsonUtils.dtoObjectMapper.readTree(expectedNode), nodeWithoutContributorsPreview);
  }

  @Test
  void shouldReturnInitialNodeWhenProvidedNodeIsNotObjectNode() throws JsonProcessingException {
    var json =
        """
             "notObjectNode": "Node"
        """;
    var node = JsonUtils.dtoObjectMapper.readTree(json);

    var producedNode = ExpandedDocumentTool.prepareJsonNodeForModel(node);

    assertEquals(node, producedNode);
  }
}
