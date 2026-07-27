package no.sikt.nva.nvi.index.mapper;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps contributors for the index document into two independent lists:
 *
 * <ul>
 *   <li>{@code nviContributors} - one per NVI creator on the frozen Candidate, carrying the name,
 *       orcid, role and affiliations persisted on the Candidate, with fallback to the matching
 *       Publication creator for data not yet migrated to the Candidate. Indexed even if the
 *       Publication no longer has the creator.
 *   <li>{@code contributors} - the full Publication author list as plain {@link Contributor}s
 *       carrying the Publication's current data.
 * </ul>
 *
 * <p>An NVI creator therefore appears in both lists: under their frozen Candidate name in {@code
 * nviContributors} and their current Publication name in {@code contributors}, so a search on
 * either name finds the candidate.
 */
final class ContributorMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContributorMapper.class);
  private final Candidate candidate;
  private final Collection<ContributorDto> publicationContributors;

  ContributorMapper(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationContributors =
        Optional.ofNullable(publicationDto).map(PublicationDto::contributors).orElse(emptyList());
  }

  List<Contributor> mapPublicationContributors() {
    return publicationContributors.stream().map(ContributorMapper::buildContributor).toList();
  }

  List<NviContributor> mapNviContributors() {
    return candidate.publicationDetails().nviCreators().stream()
        .map(this::buildNviContributor)
        .toList();
  }

  private NviContributor buildNviContributor(NviCreator creator) {
    var enrichment = findEnrichmentContributor(creator).orElse(null);
    return NviContributor.builder()
        .withId(creator.id())
        .withName(extractName(creator, enrichment))
        .withOrcid(extractOrcid(creator, enrichment))
        .withRole(creator.role().value())
        .withAffiliations(buildNviCreatorAffiliations(creator))
        .build();
  }

  private Optional<ContributorDto> findEnrichmentContributor(NviCreator creator) {
    return publicationContributors.stream()
        .filter(ContributorDto::isCreator)
        .filter(contributorDto -> matches(creator, contributorDto))
        .findFirst();
  }

  private static boolean matches(NviCreator creator, ContributorDto contributorDto) {
    return creator.isVerified() && creator.id().equals(contributorDto.id());
  }

  private static Contributor buildContributor(ContributorDto contributorDto) {
    return Contributor.builder()
        .withId(contributorDto.id())
        .withName(contributorDto.name())
        .withOrcid(orcidFromPublication(contributorDto).orElse(null))
        .withRole(roleFromPublication(contributorDto).orElse(null))
        .withAffiliations(buildSimpleAffiliations(contributorDto))
        .build();
  }

  // TODO: NP-51414 - Remove fallback when names are migrated
  private static String extractName(NviCreator creator, ContributorDto contributorDto) {
    return creatorNameFromCandidate(creator)
        .or(() -> creatorNameFromPublication(contributorDto))
        .orElse(null);
  }

  private static Optional<String> creatorNameFromCandidate(NviCreator creator) {
    return Optional.ofNullable(creator).map(NviCreator::name).filter(StringUtils::isNotBlank);
  }

  private static Optional<String> creatorNameFromPublication(ContributorDto contributor) {
    return Optional.ofNullable(contributor)
        .map(ContributorDto::name)
        .filter(StringUtils::isNotBlank);
  }

  // TODO: NP-51468 - Remove fallback when ORCID is migrated
  private static String extractOrcid(NviCreator creator, ContributorDto contributorDto) {
    return orcidFromCandidate(creator).or(() -> orcidFromPublication(contributorDto)).orElse(null);
  }

  private static Optional<String> orcidFromCandidate(NviCreator creator) {
    return Optional.ofNullable(creator).map(NviCreator::orcid).map(Object::toString);
  }

  private static Optional<String> orcidFromPublication(ContributorDto contributorDto) {
    return Optional.ofNullable(contributorDto).map(ContributorDto::orcid).map(Object::toString);
  }

  private static Optional<String> roleFromPublication(ContributorDto contributorDto) {
    return Optional.ofNullable(contributorDto)
        .map(ContributorDto::roles)
        .orElse(emptyList())
        .stream()
        .findAny()
        .map(ContributorRole::value);
  }

  /**
   * Affiliations come only from the frozen Candidate. Publication-only affiliations are excluded:
   * they carry no NVI points and the full live list is still on the plain {@link Contributor}.
   */
  private List<OrganizationType> buildNviCreatorAffiliations(NviCreator creator) {
    return creator.getAffiliationIds().stream()
        .<OrganizationType>map(affiliationId -> buildNviAffiliation(creator, affiliationId))
        .toList();
  }

  private NviOrganization buildNviAffiliation(NviCreator creator, URI affiliationId) {
    return NviOrganization.builder()
        .withId(affiliationId)
        .withPartOf(findPartOfChain(creator, affiliationId))
        .build();
  }

  /**
   * Resolves the ancestor chain from the frozen Candidate only, never the live publication, since
   * it drives report roll-up and reindexing must not change reported numbers (see NP-51406). If we
   * cannot resolve the organization hierarchy from the frozen data, it is indexed as a standalone
   * "leaf node".
   */
  private List<URI> findPartOfChain(NviCreator creator, URI affiliationId) {
    return creator
        .findAncestorsOf(affiliationId)
        .map(List::copyOf)
        .orElseGet(missingOrganizationHierarchy(affiliationId));
  }

  private Supplier<List<URI>> missingOrganizationHierarchy(URI affiliationId) {
    return () -> {
      LOGGER.error(
          "Failed to find persisted organization hierarchy for NVI affiliation {} on candidate {}",
          affiliationId,
          candidate.identifier());
      return emptyList();
    };
  }

  private static List<OrganizationType> buildSimpleAffiliations(ContributorDto contributorDto) {
    return contributorDto.affiliations().stream()
        .filter(affiliation -> nonNull(affiliation.id()))
        .map(affiliation -> buildOrganization(affiliation.id()))
        .toList();
  }

  private static OrganizationType buildOrganization(URI id) {
    return Organization.builder().withId(id).build();
  }
}
