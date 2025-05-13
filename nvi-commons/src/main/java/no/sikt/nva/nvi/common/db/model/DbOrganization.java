package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbOrganization.Builder.class)
public record DbOrganization(
    URI id,
    String countryCode,
    List<DbOrganization> parentOrganizations,
    List<DbOrganization> subOrganizations,
    Map<String, String> labels) {

  public DbOrganization {
    // Leaf nodes in the organization tree will have null values
    parentOrganizations = Optional.ofNullable(parentOrganizations).map(List::copyOf).orElse(null);
    subOrganizations = Optional.ofNullable(subOrganizations).map(List::copyOf).orElse(null);
    labels = Optional.ofNullable(labels).map(Map::copyOf).orElse(null);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String countryCode;
    private List<DbOrganization> parentOrganizations;
    private List<DbOrganization> subOrganizations;
    private Map<String, String> labels;

    private Builder() {}

    public Builder id(URI id) {
      this.id = id;
      return this;
    }

    public Builder countryCode(String countryCode) {
      this.countryCode = countryCode;
      return this;
    }

    public Builder parentOrganizations(List<DbOrganization> parentOrganizations) {
      this.parentOrganizations = parentOrganizations;
      return this;
    }

    public Builder subOrganizations(List<DbOrganization> subOrganizations) {
      this.subOrganizations = subOrganizations;
      return this;
    }

    public Builder labels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public DbOrganization build() {
      return new DbOrganization(id, countryCode, parentOrganizations, subOrganizations, labels);
    }
  }
}
