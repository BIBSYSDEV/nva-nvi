package no.sikt.nva.nvi.common.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomNonNviContributor;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_RANGE_AS_DTO;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.unit.nva.testutils.RandomDataGenerator.randomIsbn13;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class PublicationDetailsFixtures {

  public static PublicationDto.Builder publicationDtoMatchingCandidate(Candidate candidate) {
    var topLevelOrgs = candidate.publicationDetails().topLevelOrganizations();
    var contributors = buildContributorsFromCandidate(candidate, topLevelOrgs);
    var channels = buildChannelsFromCandidate(candidate);
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus("Published")
        .withContributors(contributors)
        .withPublicationChannels(channels)
        .withTopLevelOrganizations(topLevelOrgs);
  }

  public static PublicationDto.Builder randomPublicationDtoBuilder() {
    var channel =
        PublicationChannelDto.builder()
            .withId(randomUri())
            .withChannelType(ChannelType.JOURNAL)
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .build();
    var organization = randomTopLevelOrganization();
    return PublicationDto.builder()
        .withId(randomUri())
        .withIdentifier(randomUUID().toString())
        .withContributors(List.of(verifiedCreatorFrom(organization)))
        .withTopLevelOrganizations(List.of(organization))
        .withPublicationChannels(List.of(channel))
        .withPublicationType(InstanceType.ACADEMIC_ARTICLE)
        .withModifiedDate(Instant.now())
        .withPageCount(PAGE_RANGE_AS_DTO)
        .withPublicationDate(getRandomDateInCurrentYearAsDto())
        .withAbstract(randomString())
        .withLanguage(null)
        .withStatus("PUBLISHED")
        .withTitle(randomString())
        .withIsApplicable(true)
        .withIsInternationalCollaboration(false)
        .withIsbnList(List.of(randomIsbn13()));
  }

  private static List<ContributorDto> buildContributorsFromCandidate(
      Candidate candidate, Collection<Organization> topLevelOrgs) {
    var contributors = new ArrayList<ContributorDto>();
    candidate
        .publicationDetails()
        .verifiedCreators()
        .forEach(creator -> contributors.add(contributorFrom(creator, topLevelOrgs)));
    candidate
        .publicationDetails()
        .unverifiedCreators()
        .forEach(creator -> contributors.add(contributorFrom(creator, topLevelOrgs)));
    for (var i = 0; i < 10; i++) {
      contributors.add(randomNonNviContributor(Collections.emptyList()));
    }
    return contributors;
  }

  // Real PublicationDto data carries a populated partOf chain on each affiliation; building these
  // realistically lets consumers trust org.flattenPartOfChain().
  private static ContributorDto contributorFrom(
      NviCreatorDto creator, Collection<Organization> topLevelOrgs) {
    var builder =
        ContributorDto.builder()
            .withName(creator.name())
            .withRole(new ContributorRole(randomString()))
            .withAffiliations(hydrateAffiliations(creator.affiliations(), topLevelOrgs));
    if (creator instanceof VerifiedNviCreatorDto verified) {
      builder.withId(verified.id());
    }
    return builder.build();
  }

  private static List<Organization> hydrateAffiliations(
      List<URI> affiliationUris, Collection<Organization> topLevelOrgs) {
    return affiliationUris.stream()
        .map(
            uri ->
                findAffiliationPath(uri, topLevelOrgs)
                    .map(PublicationDetailsFixtures::buildNestedOrganization)
                    .orElseGet(() -> Organization.builder().withId(uri).build()))
        .toList();
  }

  /** Returns the chain of organization URIs from top-level down to (and including) the target. */
  private static Optional<List<URI>> findAffiliationPath(
      URI target, Collection<Organization> topLevelOrgs) {
    return topLevelOrgs.stream()
        .map(top -> findPath(target, top))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static Optional<List<URI>> findPath(URI target, Organization current) {
    if (current.id().equals(target)) {
      var path = new ArrayList<URI>();
      path.add(current.id());
      return Optional.of(path);
    }
    if (isNull(current.hasPart())) {
      return Optional.empty();
    }
    return current.hasPart().stream()
        .map(child -> findPath(target, child))
        .flatMap(Optional::stream)
        .findFirst()
        .map(
            childPath -> {
              var path = new ArrayList<URI>();
              path.add(current.id());
              path.addAll(childPath);
              return path;
            });
  }

  /**
   * Path is top-down (e.g. [root, intermediate, leaf]); builds a nested Organization rooted at the
   * leaf.
   */
  private static Organization buildNestedOrganization(List<URI> pathTopDown) {
    Organization parent = null;
    for (var orgId : pathTopDown) {
      var builder = Organization.builder().withId(orgId);
      if (nonNull(parent)) {
        builder.withPartOf(List.of(parent));
      }
      parent = builder.build();
    }
    return parent;
  }

  private static List<PublicationChannelDto> buildChannelsFromCandidate(Candidate candidate) {
    var channel = candidate.getPublicationChannel();
    if (isNull(channel.id()) && isNull(channel.channelType())) {
      return Collections.emptyList();
    }
    var channelDto =
        PublicationChannelDto.builder()
            .withId(nonNull(channel.id()) ? channel.id() : randomUri())
            .withChannelType(
                nonNull(channel.channelType()) ? channel.channelType() : ChannelType.JOURNAL)
            .withScientificValue(channel.scientificValue())
            .withName(randomString())
            .withPrintIssn(randomString())
            .build();
    return List.of(channelDto);
  }
}
