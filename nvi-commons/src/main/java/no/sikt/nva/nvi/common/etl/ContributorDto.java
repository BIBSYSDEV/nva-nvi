package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Contributor")
public record ContributorDto(
    URI id,
    String name,
    String verificationStatus,
    ContributorRole role,
    List<Organization> affiliations) {
  private static final String VERIFIED = "Verified";

  public ContributorDto {
    requireNonNull(affiliations, "Required field 'affiliations' is null");
  }

  public void validate() {
    if (isBlank(name)) {
      requireNonNull(id, "Both 'id' and 'name' is null, one of these fields must be set");
    }
    requireNonNull(verificationStatus, "Required field 'verificationStatus' is null");
    requireNonNull(role, "Required field 'role' is null");
    requireNonNull(affiliations, "Required field 'affiliations' is null");
  }

  @JsonIgnore
  public boolean isCreator() {
    return nonNull(role) && role.isCreator();
  }

  @JsonIgnore
  public boolean isVerified() {
    return nonNull(id)
        && nonNull(verificationStatus)
        && VERIFIED.equalsIgnoreCase(verificationStatus);
  }

  @JsonIgnore
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String name;
    private String verificationStatus;
    private ContributorRole role;
    private List<Organization> affiliations;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withVerificationStatus(String verificationStatus) {
      this.verificationStatus = verificationStatus;
      return this;
    }

    public Builder withRole(ContributorRole role) {
      this.role = role;
      return this;
    }

    public Builder withAffiliations(List<Organization> affiliations) {
      this.affiliations = affiliations;
      return this;
    }

    public ContributorDto build() {
      return new ContributorDto(id, name, verificationStatus, role, affiliations);
    }
  }
}
