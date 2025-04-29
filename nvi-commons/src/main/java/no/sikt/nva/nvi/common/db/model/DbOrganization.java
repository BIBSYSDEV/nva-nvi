package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbOrganization.Builder.class)
public record DbOrganization(
    URI id,
    String countryCode,
    List<DbOrganization> partOf,
    List<DbOrganization> hasPart,
    Map<String, String> labels) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI builderId;
    private String builderCountryCode;
    private List<DbOrganization> builderPartOf;
    private List<DbOrganization> builderHasPart;
    private Map<String, String> builderLabels;

    private Builder() {}

    public Builder id(URI id) {
      this.builderId = id;
      return this;
    }

    public Builder countryCode(String countryCode) {
      this.builderCountryCode = countryCode;
      return this;
    }

    public Builder partOf(List<DbOrganization> partOf) {
      this.builderPartOf = List.copyOf(partOf);
      return this;
    }

    public Builder hasPart(List<DbOrganization> hasPart) {
      this.builderHasPart = List.copyOf(hasPart);
      return this;
    }

    public Builder labels(Map<String, String> labels) {
      this.builderLabels = Map.copyOf(labels);
      return this;
    }

    public DbOrganization build() {
      return new DbOrganization(
          builderId, builderCountryCode, builderPartOf, builderHasPart, builderLabels);
    }
  }
}
