package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static nva.commons.core.StringUtils.isBlank;
import static nva.commons.core.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Contributor")
public record ContributorDto(
    URI id,
    String name,
    VerificationStatus verificationStatus,
    ContributorRole role,
    Collection<Organization> affiliations) {

  public ContributorDto {
    if (isNull(affiliations)) {
      affiliations = emptyList();
    }
  }

  public void validate() {
    if (isBlank(name)) {
      shouldNotBeNull(id, "Both 'id' and 'name' is null, one of these fields must be set");
    }
  }

  @JsonIgnore
  public boolean isCreator() {
    return role.isCreator();
  }

  @JsonIgnore
  public boolean isVerified() {
    return nonNull(id) && verificationStatus.isVerified();
  }

  @JsonIgnore
  public boolean isNamed() {
    return isNotBlank(name);
  }

  @JsonIgnore
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String name;
    private VerificationStatus verificationStatus;
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

    public Builder withVerificationStatus(VerificationStatus verificationStatus) {
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
