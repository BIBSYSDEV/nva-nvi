package no.sikt.nva.nvi.common.model;

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
        .withAffiliations(List.of(affiliations))
        .build();
  }
}
