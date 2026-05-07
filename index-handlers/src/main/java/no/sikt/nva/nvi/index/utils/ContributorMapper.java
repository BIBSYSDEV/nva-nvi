package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;

final class ContributorMapper {

  private final Candidate candidate;
  private final PublicationDto publicationDto;

  ContributorMapper(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationDto = publicationDto;
  }

  List<ContributorType> mapContributors() {
    return publicationDto.contributors().stream().map(this::mapContributor).toList();
  }

  private ContributorType mapContributor(ContributorDto contributorDto) {
    return findMatchingNviCreator(contributorDto)
        .map(nviCreator -> buildNviContributor(contributorDto, nviCreator))
        .orElseGet(() -> buildNonNviContributor(contributorDto));
  }

  private Optional<NviCreatorDto> findMatchingNviCreator(ContributorDto contributorDto) {
    return findMatchingVerifiedCreator(contributorDto)
        .map(NviCreatorDto.class::cast)
        .or(() -> findMatchingUnverifiedCreator(contributorDto).map(NviCreatorDto.class::cast));
  }

  private Optional<VerifiedNviCreatorDto> findMatchingVerifiedCreator(
      ContributorDto contributorDto) {
    if (isNull(contributorDto.id())) {
      return Optional.empty();
    }
    return candidate.publicationDetails().verifiedCreators().stream()
        .filter(creator -> creator.id().equals(contributorDto.id()))
        .findFirst();
  }

  private Optional<UnverifiedNviCreatorDto> findMatchingUnverifiedCreator(
      ContributorDto contributorDto) {
    if (isNull(contributorDto.name())) {
      return Optional.empty();
    }
    return candidate.publicationDetails().unverifiedCreators().stream()
        .filter(creator -> creator.name().equals(contributorDto.name()))
        .findFirst();
  }

  private static ContributorType buildNviContributor(
      ContributorDto contributorDto, NviCreatorDto nviCreator) {
    var creatorId =
        nviCreator instanceof VerifiedNviCreatorDto verified ? verified.id().toString() : null;
    return NviContributor.builder()
        .withId(creatorId)
        .withName(nviCreator.name())
        .withOrcid(extractOrcid(contributorDto))
        .withRole(extractRole(contributorDto))
        .withAffiliations(buildNviAffiliations(contributorDto, nviCreator))
        .build();
  }

  // TODO: Remove when non-NVI contributor data is available from Candidate
  private static ContributorType buildNonNviContributor(ContributorDto contributorDto) {
    var contributorId = nonNull(contributorDto.id()) ? contributorDto.id().toString() : null;
    return Contributor.builder()
        .withId(contributorId)
        .withName(contributorDto.name())
        .withOrcid(extractOrcid(contributorDto))
        .withRole(extractRole(contributorDto))
        .withAffiliations(buildSimpleAffiliations(contributorDto))
        .build();
  }

  private static String extractOrcid(ContributorDto contributorDto) {
    return nonNull(contributorDto.orcid()) ? contributorDto.orcid().toString() : null;
  }

  // TODO: Remove when role is available from Candidate
  private static String extractRole(ContributorDto contributorDto) {
    return Optional.ofNullable(contributorDto.roles()).orElse(Collections.emptyList()).stream()
        .findFirst()
        .map(ContributorRole::value)
        .orElse(null);
  }

  private static List<OrganizationType> buildNviAffiliations(
      ContributorDto contributorDto, NviCreatorDto nviCreator) {
    var nviAffiliationUris = nviCreator.affiliations();
    return contributorDto.affiliations().stream()
        .filter(affiliation -> nonNull(affiliation.id()))
        .map(affiliation -> buildAffiliation(affiliation, nviAffiliationUris))
        .toList();
  }

  private static OrganizationType buildAffiliation(
      Organization affiliation, List<java.net.URI> nviAffiliationUris) {
    if (nviAffiliationUris.contains(affiliation.id())) {
      return NviOrganization.builder()
          .withId(affiliation.id())
          .withPartOf(affiliation.flattenPartOfChain())
          .build();
    }
    return no.sikt.nva.nvi.index.model.document.Organization.builder()
        .withId(affiliation.id())
        .build();
  }

  private static List<OrganizationType> buildSimpleAffiliations(ContributorDto contributorDto) {
    return contributorDto.affiliations().stream()
        .filter(affiliation -> nonNull(affiliation.id()))
        .<OrganizationType>map(
            affiliation ->
                no.sikt.nva.nvi.index.model.document.Organization.builder()
                    .withId(affiliation.id())
                    .build())
        .toList();
  }
}
