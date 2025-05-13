package no.sikt.nva.nvi.test;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LABELS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PART_OF_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotNull;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public record SampleExpandedAffiliation(
    URI id, List<URI> partOf, String countryCode, Map<String, String> labels) {
  private static final String AFFILIATION_NODE_TYPE = "Organization";

  public static Builder builder() {
    return new Builder();
  }

  private static ObjectNode createAffiliationLeafNode(URI id) {
    var affiliationNode = objectMapper.createObjectNode();
    putIfNotNull(affiliationNode, ID_FIELD, id);
    return affiliationNode;
  }

  public ObjectNode asObjectNode() {
    var affiliationNode = createNodeWithType(AFFILIATION_NODE_TYPE);
    putIfNotNull(affiliationNode, ID_FIELD, id);
    putIfNotBlank(affiliationNode, COUNTRY_CODE_FIELD, countryCode);

    if (nonNull(partOf) && !partOf.isEmpty()) {
      var partOfNode = objectMapper.createArrayNode();
      partOf.stream()
          .map(SampleExpandedAffiliation::createAffiliationLeafNode)
          .forEach(partOfNode::add);
      affiliationNode.set(PART_OF_FIELD, partOfNode);
    }

    if (nonNull(labels) && !labels.isEmpty()) {
      var labelsNode = objectMapper.createObjectNode();
      labels.forEach(labelsNode::put);
      affiliationNode.set(LABELS_FIELD, labelsNode);
    }

    return affiliationNode;
  }

  public static final class Builder {

    private URI id = randomUri();
    private List<URI> partOf = emptyList();
    private String countryCode = COUNTRY_CODE_NORWAY;
    private Map<String, String> labels;

    private Builder() {}

    public Builder withPartOf(Collection<URI> partOf) {
      this.partOf = partOf.stream().toList();
      return this;
    }

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withCountryCode(String countryCode) {
      this.countryCode = countryCode;
      return this;
    }

    public Builder withLabels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public SampleExpandedAffiliation build() {
      return new SampleExpandedAffiliation(id, partOf, countryCode, labels);
    }
  }
}
