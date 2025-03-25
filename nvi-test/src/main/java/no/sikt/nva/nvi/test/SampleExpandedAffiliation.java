package no.sikt.nva.nvi.test;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LABELS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record SampleExpandedAffiliation(
    URI id, List<URI> partOf, String countryCode, Map<String, String> labels) {
  private static final String TYPE_VALUE = "Organization";

  public static Builder builder() {
    return new Builder();
  }

  public static Builder from(SampleExpandedAffiliation other) {
    return new Builder(other);
  }

  public ObjectNode asObjectNode() {
    var affiliationNode = objectMapper.createObjectNode();
    affiliationNode.put(TYPE_FIELD, TYPE_VALUE);

    if (nonNull(id)) {
      affiliationNode.put(ID_FIELD, id.toString());
    }

    if (nonNull(partOf) && !partOf.isEmpty()) {
      var partOfNode = objectMapper.createArrayNode();
      partOf.forEach(uri -> partOfNode.add(uri.toString()));
      affiliationNode.set("partOf", partOfNode);
    }

    if (nonNull(countryCode)) {
      affiliationNode.put(COUNTRY_CODE_FIELD, countryCode);
    }

    if (nonNull(labels)) {
      var labelsNode = objectMapper.createObjectNode();
      labels.forEach(labelsNode::put);
      affiliationNode.set(LABELS_FIELD, labelsNode);
    }

    return affiliationNode;
  }

  @JacocoGenerated
  public static final class Builder {

    private URI id = randomUri();
    private List<URI> partOf = emptyList();
    private String countryCode = COUNTRY_CODE_NORWAY;
    private Map<String, String> labels = Map.of();

    private Builder() {}

    private Builder(SampleExpandedAffiliation other) {
      this.id = other.id();
      this.countryCode = other.countryCode();
      this.labels = other.labels();
    }

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

    public SampleExpandedAffiliation build() {
      return new SampleExpandedAffiliation(id, partOf, countryCode, labels);
    }
  }
}
