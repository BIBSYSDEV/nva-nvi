package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.randomContributorId;
import static no.sikt.nva.nvi.test.TestUtils.randomName;

import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.VerificationStatus;

public final class ContributorFixtures {
  public static final ContributorRole ROLE_CREATOR = new ContributorRole("Creator");
  public static final ContributorRole ROLE_OTHER = new ContributorRole("ContactPerson");
  public static final VerificationStatus STATUS_VERIFIED = new VerificationStatus("Verified");
  public static final VerificationStatus STATUS_UNVERIFIED = new VerificationStatus("NotVerified");

  private ContributorFixtures() {}

  public static ContributorDto.Builder randomContributorDtoBuilder(Organization... affiliations) {
    return ContributorDto.builder()
        .withId(randomContributorId())
        .withName(randomName())
        .withRole(ROLE_CREATOR)
        .withVerificationStatus(STATUS_VERIFIED)
        .withAffiliations(List.of(affiliations));
  }

  public static ContributorDto verifiedCreatorFrom(Organization... affiliations) {
    return randomContributorDtoBuilder(affiliations).build();
  }

  public static ContributorDto unverifiedCreatorFrom(Organization... affiliations) {
    return randomContributorDtoBuilder(affiliations)
        .withId(null)
        .withVerificationStatus(STATUS_UNVERIFIED)
        .build();
  }

  public static ContributorDto mapToContributorDto(NviCreator nviCreator) {
    var affiliations =
        nviCreator.nviAffiliations().stream()
            .map(OrganizationFixtures::getAsOrganizationLeafNode)
            .toList();
    return ContributorDto.builder()
        .withId(nviCreator.id())
        .withName(nviCreator.name())
        .withVerificationStatus(nviCreator.verificationStatus())
        .withRole(ROLE_CREATOR)
        .withAffiliations(affiliations)
        .build();
  }
}
