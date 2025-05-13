package no.sikt.nva.nvi.test;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    return objectMapper.valueToTree(this);
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
      return withParentOrganizations(List.of(parentIds));
    }

    public Builder withParentOrganizations(Collection<URI> parentIds) {
      this.parentOrganizations =
          parentIds.stream().map(parentId -> builder().withId(parentId).build()).toList();
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
