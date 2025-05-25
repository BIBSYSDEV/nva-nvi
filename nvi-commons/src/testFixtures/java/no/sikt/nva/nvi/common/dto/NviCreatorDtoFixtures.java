package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

public class NviCreatorDtoFixtures {

  public static VerifiedNviCreatorDto verifiedNviCreatorDtoCopiedFrom(
      VerifiedNviCreatorDto originalCreator, Collection<URI> newAffiliations) {
    return new VerifiedNviCreatorDto(
        originalCreator.id(), originalCreator.name(), List.copyOf(newAffiliations));
  }

  public static VerifiedNviCreatorDto verifiedNviCreatorDtoCopiedFrom(
      VerifiedNviCreatorDto originalCreator, Organization... newAffiliations) {
    var affiliationIds = List.of(newAffiliations).stream().map(Organization::id).toList();
    return new VerifiedNviCreatorDto(originalCreator.id(), originalCreator.name(), affiliationIds);
  }

  public static VerifiedNviCreatorDto verifiedNviCreatorDtoFrom(Collection<URI> affiliations) {
    return new VerifiedNviCreatorDto(
        randomUriWithSuffix("creatorId"), randomString(), List.copyOf(affiliations));
  }

  public static VerifiedNviCreatorDto verifiedNviCreatorDtoFrom(URI... affiliations) {
    return verifiedNviCreatorDtoFrom(List.of(affiliations));
  }

  public static VerifiedNviCreatorDto verifiedNviCreatorDtoFrom(Organization... affiliations) {
    var creatorId = randomUriWithSuffix("creatorId");
    var affiliationIds = List.of(affiliations).stream().map(Organization::id).toList();
    return new VerifiedNviCreatorDto(creatorId, randomString(), affiliationIds);
  }

  public static UnverifiedNviCreatorDto unverifiedNviCreatorDtoFrom(Collection<URI> affiliations) {
    return new UnverifiedNviCreatorDto(randomString(), List.copyOf(affiliations));
  }

  public static UnverifiedNviCreatorDto unverifiedNviCreatorDtoFrom(URI... affiliations) {
    return unverifiedNviCreatorDtoFrom(List.of(affiliations));
  }

  public static UnverifiedNviCreatorDto unverifiedNviCreatorDtoFrom(Organization... affiliations) {
    var affiliationIds = List.of(affiliations).stream().map(Organization::id).toList();
    return unverifiedNviCreatorDtoFrom(affiliationIds);
  }
}
