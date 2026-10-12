package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.TestConstants.YEAR_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record SampleExpandedPublicationDate(String year, String month, String day) {

  public ObjectNode asObjectNode() {
    var node = createNodeWithType("PublicationDate");
    node.put(YEAR_FIELD, year);
    putIfNotBlank(node, "month", month);
    putIfNotBlank(node, "day", day);
    return node;
  }
}
