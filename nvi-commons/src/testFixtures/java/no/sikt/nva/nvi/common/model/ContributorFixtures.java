package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorDto.Builder;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.VerificationStatus;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

public class ContributorFixtures {
  public static final ContributorRole ROLE_CREATOR = new ContributorRole("Creator");
  public static final ContributorRole ROLE_OTHER = new ContributorRole("ContactPerson");
  public static final VerificationStatus STATUS_VERIFIED = new VerificationStatus("Verified");
  public static final VerificationStatus STATUS_UNVERIFIED = new VerificationStatus("NotVerified");

  public static Builder randomCreator(Organization... affiliations) {
    return randomCreator(List.of(affiliations));
  }

  public static Builder randomCreator(List<Organization> affiliations) {
    return ContributorDto.builder()
        .withId(randomUri())
        .withName(randomString())
        .withRole(ROLE_CREATOR)
        .withVerificationStatus(STATUS_VERIFIED)
        .withAffiliations(affiliations);
  }

  public static VerifiedNviCreatorDto randomVerifiedNviCreatorDto(URI... affiliations) {
    return VerifiedNviCreatorDto.builder()
        .withId(randomUri())
        .withName(randomString())
        .withAffiliations(List.of(affiliations))
        .build();
  }

  public static ContributorDtoBuilder randomContributorDtoBuilder(Organization... affiliations) {
    return builder()
        .withId(randomUri())
        .withName(randomString())
        .withRole(ROLE_CREATOR)
        .withVerificationStatus(STATUS_VERIFIED)
        .withAffiliations(List.of(affiliations));
  }

  public static ContributorDto verifiedCreatorFrom(Organization... affiliations) {
    var creatorId = randomUriWithSuffix("creatorId");
    return new ContributorDto(
        creatorId, randomString(), STATUS_VERIFIED, ROLE_CREATOR, List.of(affiliations));
  }

  public static ContributorDto unverifiedCreatorFrom(Organization... affiliations) {
    return new ContributorDto(
        null, randomString(), STATUS_UNVERIFIED, ROLE_CREATOR, List.of(affiliations));
  }

  public static ContributorDto mapToContributorDto(NviCreator nviCreator) {
    return new ContributorDto(
        nviCreator.id(),
        nviCreator.name(),
        nviCreator.verificationStatus(),
        ROLE_CREATOR,
        nviCreator.nviAffiliations());
  }

  public static ContributorDtoBuilder builder() {
    return new ContributorDtoBuilder();
  }

  public static final class ContributorDtoBuilder {

    private URI id;
    private String name;
    private VerificationStatus verificationStatus;
    private ContributorRole role;
    private List<Organization> affiliations;

    public ContributorDtoBuilder withId(URI id) {
      this.id = id;
      return this;
    }

    public ContributorDtoBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public ContributorDtoBuilder withVerificationStatus(VerificationStatus verificationStatus) {
      this.verificationStatus = verificationStatus;
      return this;
    }

    public ContributorDtoBuilder withRole(ContributorRole role) {
      this.role = role;
      return this;
    }

    public ContributorDtoBuilder withAffiliations(List<Organization> affiliations) {
      this.affiliations = affiliations;
      return this;
    }

    public ContributorDto build() {
      return new ContributorDto(id, name, verificationStatus, role, affiliations);
    }
  }
}
