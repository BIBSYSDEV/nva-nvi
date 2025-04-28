package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record SampleExpandedPageCount(String begin, String end, String numberOfPages) {

  public ObjectNode asPageRange() {
    var node = createNodeWithType("Range");
    putIfNotBlank(node, "begin", begin);
    putIfNotBlank(node, "end", end);
    return node;
  }

  public ObjectNode asMonographPages() {
    var node = createNodeWithType("MonographPages");
    putIfNotBlank(node, "pages", numberOfPages);
    return node;
  }
}
