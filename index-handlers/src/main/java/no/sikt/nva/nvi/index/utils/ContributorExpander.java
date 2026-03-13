package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;

final class ContributorExpander {

  private final Candidate candidate;
  private final PublicationDto publicationDto;

  ContributorExpander(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationDto = publicationDto;
  }

  List<ContributorType> expandContributors() {
    return publicationDto.contributors().stream().map(this::createContributor).toList();
  }

  private ContributorType createContributor(ContributorDto contributor) {
    return findMatchingNviCreator(contributor)
        .map(nviCreator -> generateNviContributor(contributor, nviCreator))
        .orElseGet(() -> generateContributor(contributor));
  }

  private Optional<NviCreatorDto> findMatchingNviCreator(ContributorDto contributor) {
    return findVerifiedNviCreator(contributor)
        .map(NviCreatorDto.class::cast)
        .or(() -> findUnverifiedNviCreator(contributor).map(NviCreatorDto.class::cast));
  }

  private Optional<VerifiedNviCreatorDto> findVerifiedNviCreator(ContributorDto contributor) {
    if (isNull(contributor.id())) {
      return Optional.empty();
    }
    return candidate.publicationDetails().verifiedCreators().stream()
        .filter(creator -> creator.id().equals(contributor.id()))
        .findFirst();
  }

  private Optional<UnverifiedNviCreatorDto> findUnverifiedNviCreator(ContributorDto contributor) {
    if (isNull(contributor.name())) {
      return Optional.empty();
    }
    return candidate.publicationDetails().unverifiedCreators().stream()
        .filter(creator -> creator.name().equals(contributor.name()))
        .findFirst();
  }

  private static ContributorType generateNviContributor(
      ContributorDto contributor, NviCreatorDto nviCreator) {
    return NviContributor.builder()
        .withId(nonNull(contributor.id()) ? contributor.id().toString() : null)
        .withName(contributor.name())
        .withOrcid(contributor.orcid())
        .withRole(contributor.role().getValue())
        .withAffiliations(expandNviAffiliations(contributor.affiliations(), nviCreator))
        .build();
  }

  private static ContributorType generateContributor(ContributorDto contributor) {
    return Contributor.builder()
        .withId(nonNull(contributor.id()) ? contributor.id().toString() : null)
        .withName(contributor.name())
        .withOrcid(contributor.orcid())
        .withRole(contributor.role().getValue())
        .withAffiliations(expandSimpleAffiliations(contributor.affiliations()))
        .build();
  }

  private static List<OrganizationType> expandNviAffiliations(
      Collection<no.sikt.nva.nvi.common.client.model.Organization> affiliations,
      NviCreatorDto nviCreator) {
    return affiliations.stream()
        .filter(org -> nonNull(org.id()))
        .map(org -> expandNviAffiliation(org, nviCreator))
        .toList();
  }

  private static OrganizationType expandNviAffiliation(
      no.sikt.nva.nvi.common.client.model.Organization org, NviCreatorDto nviCreator) {
    if (nviCreator.affiliations().contains(org.id())) {
      return NviOrganization.builder()
          .withId(org.id())
          .withPartOf(org.flattenPartOfChain())
          .build();
    }
    return Organization.builder().withId(org.id()).build();
  }

  private static List<OrganizationType> expandSimpleAffiliations(
      Collection<no.sikt.nva.nvi.common.client.model.Organization> affiliations) {
    return affiliations.stream()
        .filter(org -> nonNull(org.id()))
        .map(org -> (OrganizationType) Organization.builder().withId(org.id()).build())
        .toList();
  }
}
