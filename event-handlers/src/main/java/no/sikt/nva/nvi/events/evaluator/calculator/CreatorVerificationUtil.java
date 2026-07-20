package no.sikt.nva.nvi.events.evaluator.calculator;

import static java.util.function.Predicate.not;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.Customer;
import no.sikt.nva.nvi.common.model.NviCreator;

public final class CreatorVerificationUtil {

  private CreatorVerificationUtil() {}

  public static List<NviCreator> getVerifiedCreators(Collection<NviCreator> creators) {
    return creators.stream().filter(NviCreator::isVerified).toList();
  }

  public static List<NviCreator> getUnverifiedCreators(Collection<NviCreator> creators) {
    return creators.stream().filter(not(NviCreator::isVerified)).toList();
  }

  public static List<NviCreator> getNviCreatorsWithNviInstitutions(
      Map<URI, Customer> customers, PublicationDto publication) {
    var topLevelNviOrganizations = getTopLevelNviOrganizations(customers, publication);
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .filter(CreatorVerificationUtil::isValidContributor)
        .map(contributor -> toNviCreator(topLevelNviOrganizations, contributor))
        .filter(CreatorVerificationUtil::isAffiliatedWithNviOrganization)
        .toList();
  }

  private static List<Organization> getTopLevelNviOrganizations(
      Map<URI, Customer> customers, PublicationDto publication) {
    return publication.topLevelOrganizations().stream()
        .filter(isNviInstitution(customers))
        .toList();
  }

  private static boolean isValidContributor(ContributorDto contributorDto) {
    return contributorDto.isVerified() || contributorDto.isNamed();
  }

  private static boolean isAffiliatedWithNviOrganization(NviCreator creator) {
    return !creator.nviAffiliations().isEmpty();
  }

  private static NviCreator toNviCreator(
      Collection<Organization> topLevelNviOrganizations, ContributorDto contributor) {
    var nviAffiliations =
        contributor.affiliations().stream()
            .filter(affiliation -> hasTopLevelOrganizationIn(topLevelNviOrganizations, affiliation))
            .toList();
    var creatorOrganizations =
        topLevelNviOrganizations.stream()
            .filter(isTopLevelOrganizationOfAny(nviAffiliations))
            .toList();
    return new NviCreator(
        contributor.id(),
        contributor.name(),
        contributor.verificationStatus(),
        nviAffiliations.stream().map(Organization::id).toList(),
        creatorOrganizations);
  }

  private static boolean hasTopLevelOrganizationIn(
      Collection<Organization> organizations, Organization affiliation) {
    var topLevelOrganizationId = affiliation.getTopLevelOrg().id();
    return organizations.stream()
        .anyMatch(organization -> organization.id().equals(topLevelOrganizationId));
  }

  private static Predicate<Organization> isTopLevelOrganizationOfAny(
      Collection<Organization> affiliations) {
    return institution ->
        affiliations.stream()
            .anyMatch(affiliation -> institution.id().equals(affiliation.getTopLevelOrg().id()));
  }

  private static Predicate<Organization> isNviInstitution(Map<URI, Customer> customers) {
    return organization ->
        customers.containsKey(organization.id())
            && customers.get(organization.id()).nviInstitution();
  }
}
