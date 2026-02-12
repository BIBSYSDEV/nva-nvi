package no.sikt.nva.nvi.events.evaluator.calculator;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.Customer;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;

public final class CreatorVerificationUtil {

  private CreatorVerificationUtil() {}

  public static List<VerifiedNviCreator> getVerifiedCreators(Collection<NviCreator> creators) {
    return creators.stream()
        .filter(VerifiedNviCreator.class::isInstance)
        .map(VerifiedNviCreator.class::cast)
        .toList();
  }

  public static List<UnverifiedNviCreator> getUnverifiedCreators(Collection<NviCreator> creators) {
    return creators.stream()
        .filter(UnverifiedNviCreator.class::isInstance)
        .map(UnverifiedNviCreator.class::cast)
        .toList();
  }

  public static List<NviCreator> getNviCreatorsWithNviInstitutions(
      Map<URI, Customer> customers, PublicationDto publication) {
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .filter(CreatorVerificationUtil::isValidContributor)
        .map(creator -> toNviCreator(customers, creator))
        .filter(CreatorVerificationUtil::isAffiliatedWithNviOrganization)
        .toList();
  }

  private static boolean isValidContributor(ContributorDto contributorDto) {
    return contributorDto.isVerified() || contributorDto.isNamed();
  }

  private static boolean isAffiliatedWithNviOrganization(NviCreator creator) {
    return !creator.nviAffiliations().isEmpty();
  }

  private static NviOrganization toNviOrganization(Organization organization) {
    return NviOrganization.builder()
        .withId(organization.id())
        .withTopLevelOrganization(
            NviOrganization.builder().withId(organization.getTopLevelOrg().id()).build())
        .build();
  }

  private static NviCreator toNviCreator(Map<URI, Customer> customers, ContributorDto contributor) {
    var nviAffiliations = getNviAffiliationsIfExist(customers, contributor);
    if (contributor.isVerified()) {
      return toVerifiedNviCreator(contributor, nviAffiliations);
    }
    return toUnverifiedNviCreator(contributor, nviAffiliations);
  }

  private static VerifiedNviCreator toVerifiedNviCreator(
      ContributorDto contributor, List<NviOrganization> nviAffiliations) {
    return VerifiedNviCreator.builder()
        .withId(contributor.id())
        .withNviAffiliations(nviAffiliations)
        .build();
  }

  private static UnverifiedNviCreator toUnverifiedNviCreator(
      ContributorDto contributor, List<NviOrganization> nviAffiliations) {
    return UnverifiedNviCreator.builder()
        .withName(contributor.name())
        .withNviAffiliations(nviAffiliations)
        .build();
  }

  private static List<NviOrganization> getNviAffiliationsIfExist(
      Map<URI, Customer> customers, ContributorDto contributor) {
    return contributor.affiliations().stream()
        .filter(isNviInstitution(customers))
        .map(CreatorVerificationUtil::toNviOrganization)
        .toList();
  }

  private static Predicate<Organization> isNviInstitution(Map<URI, Customer> customers) {
    return organization -> {
      var topLevelId = organization.getTopLevelOrg().id();
      return customers.containsKey(topLevelId) && customers.get(topLevelId).nviInstitution();
    };
  }
}
