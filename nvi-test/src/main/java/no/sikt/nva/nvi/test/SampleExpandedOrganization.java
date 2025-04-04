package no.sikt.nva.nvi.test;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record SampleExpandedOrganization(
    URI id,
    String type,
    String countryCode,
    List<SampleExpandedOrganization> hasPart,
    List<SampleExpandedOrganization> partOf,
    Map<String, String> labels) {

  public static Builder builder() {
    return new Builder();
  }

  public ObjectNode asObjectNode() {
    try {
      return objectMapper.valueToTree(this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static final class Builder {

    private URI id;
    private String type;
    private String countryCode;
    private List<SampleExpandedOrganization> parentOrganizations;
    private List<SampleExpandedOrganization> subOrganizations;
    private Map<String, String> labels;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withType() {
      this.type = "Organization";
      return this;
    }

    public Builder withCountryCode(String countryCode) {
      this.countryCode = countryCode;
      return this;
    }

    public Builder withSubOrganizations(SampleExpandedOrganization... subOrganizations) {
      this.subOrganizations = List.of(subOrganizations);
      return this;
    }

    public Builder withParentOrganizations(URI... parentIds) {
      this.parentOrganizations =
          List.of(parentIds).stream().map(id -> builder().withId(id).build()).toList();
      return this;
    }

    public Builder withLabels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public SampleExpandedOrganization build() {
      return new SampleExpandedOrganization(
          id, type, countryCode, subOrganizations, parentOrganizations, labels);
    }
  }
}
