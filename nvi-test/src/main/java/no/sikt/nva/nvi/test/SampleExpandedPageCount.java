package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record SampleExpandedPageCount(String first, String last, String total) {

  public ObjectNode asPageRange() {
    var node = createNodeWithType("Range");
    putIfNotBlank(node, "begin", first);
    putIfNotBlank(node, "end", last);
    return node;
  }

  public ObjectNode asMonographPages() {
    var node = createNodeWithType("MonographPages");
    putIfNotBlank(node, "pages", total);
    return node;
  }
}
