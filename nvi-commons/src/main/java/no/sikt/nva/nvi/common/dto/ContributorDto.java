package no.sikt.nva.nvi.common.dto;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNullElse;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static nva.commons.core.StringUtils.isBlank;
import static nva.commons.core.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

/**
 * A contribution to a publication: the identity contributed in one role, on behalf of a set of
 * affiliations. The same person can have several contributions, for example in different roles.
 */
@JsonSerialize
public record ContributorDto(
    IdentityDto identity, ContributorRole role, List<Organization> affiliations) {

  private static final IdentityDto MISSING_IDENTITY = new IdentityDto(null, null, null, null);

  public ContributorDto {
    identity = requireNonNullElse(identity, MISSING_IDENTITY);
    affiliations = copyOfNullable(affiliations);
  }

  public URI id() {
    return identity.id();
  }

  public String name() {
    return identity.name();
  }

  public URI orcid() {
    return identity.orcid();
  }

  public VerificationStatus verificationStatus() {
    return identity.verificationStatus();
  }

  public void validate() {
    if (isBlank(name())) {
      shouldNotBeNull(id(), "Both 'id' and 'name' is null, one of these fields must be set");
    }
  }

  @JsonIgnore
  public boolean isCreator() {
    return nonNull(role) && role.isCreator();
  }

  @JsonIgnore
  public boolean isVerified() {
    return nonNull(id()) && verificationStatus().isVerified();
  }

  @JsonIgnore
  public boolean isNamed() {
    return isNotBlank(name());
  }

  @JsonIgnore
  public static Builder builder() {
    return new Builder();
  }

  @JsonIgnore
  public Builder copy() {
    return builder()
        .withId(id())
        .withName(name())
        .withOrcid(orcid())
        .withVerificationStatus(verificationStatus())
        .withRole(role)
        .withAffiliations(affiliations);
  }

  public static final class Builder {

    private URI id;
    private String name;
    private URI orcid;
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

    public Builder withOrcid(URI orcid) {
      this.orcid = orcid;
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
      var identity = new IdentityDto(id, name, orcid, verificationStatus);
      return new ContributorDto(identity, role, affiliations);
    }
  }
}
