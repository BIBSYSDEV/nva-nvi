package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbContributor.Builder.class)
public record DbContributor(
    URI id,
    String name,
    String verificationStatus,
    String role,
    List<DbOrganization> affiliations) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI builderId;
    private String builderName;
    private String builderVerificationStatus;
    private String builderRole;
    private List<DbOrganization> builderAffiliations;

    private Builder() {}

    public Builder id(URI id) {
      this.builderId = id;
      return this;
    }

    public Builder name(String name) {
      this.builderName = name;
      return this;
    }

    public Builder verificationStatus(String verificationStatus) {
      this.builderVerificationStatus = verificationStatus;
      return this;
    }

    public Builder role(String role) {
      this.builderRole = role;
      return this;
    }

    public Builder affiliations(List<DbOrganization> affiliations) {
      this.builderAffiliations = List.copyOf(affiliations);
      return this;
    }

    public DbContributor build() {
      return new DbContributor(
          builderId, builderName, builderVerificationStatus, builderRole, builderAffiliations);
    }
  }
}
