package no.sikt.nva.nvi.common.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.contributorFrom;
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
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class PublicationDetailsFixtures {

  public static PublicationDto.Builder publicationDtoMatchingCandidate(Candidate candidate) {
    var topLevelOrgs = candidate.publicationDetails().topLevelOrganizations();
    var contributors =
        buildContributorsFromCandidate(candidate).stream()
            .map(contributor -> hydrateAffiliations(contributor, topLevelOrgs))
            .toList();
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

  private static List<ContributorDto> buildContributorsFromCandidate(Candidate candidate) {
    var contributors = new ArrayList<ContributorDto>();
    candidate
        .publicationDetails()
        .verifiedCreators()
        .forEach(creator -> contributors.add(contributorFrom(creator)));
    candidate
        .publicationDetails()
        .unverifiedCreators()
        .forEach(creator -> contributors.add(contributorFrom(creator)));
    for (var i = 0; i < 10; i++) {
      contributors.add(randomNonNviContributor(Collections.emptyList()));
    }
    return contributors;
  }

  /**
   * Replaces each affiliation Organization (which carries only an id) with one looked up in the
   * candidate's topLevelOrganizations tree, hydrating the partOf chain — matching the shape of
   * real-world PublicationDto data.
   */
  private static ContributorDto hydrateAffiliations(
      ContributorDto contributor, Collection<Organization> topLevelOrgs) {
    var hydratedAffiliations =
        contributor.affiliations().stream()
            .map(affiliation -> hydrateAffiliation(affiliation, topLevelOrgs))
            .toList();
    return ContributorDto.builder()
        .withId(contributor.id())
        .withName(contributor.name())
        .withOrcid(contributor.orcid())
        .withVerificationStatus(contributor.verificationStatus())
        .withRoles(contributor.roles())
        .withAffiliations(hydratedAffiliations)
        .build();
  }

  private static Organization hydrateAffiliation(
      Organization affiliation, Collection<Organization> topLevelOrgs) {
    if (isNull(affiliation.id())) {
      return affiliation;
    }
    return findAffiliationPath(affiliation.id(), topLevelOrgs)
        .map(PublicationDetailsFixtures::buildNestedOrganization)
        .orElse(affiliation);
  }

  /** Returns the chain of organization URIs from top-level down to (and including) the target. */
  private static Optional<List<URI>> findAffiliationPath(
      URI target, Collection<Organization> topLevelOrgs) {
    for (var topLevel : topLevelOrgs) {
      var pathFromTop = new ArrayList<URI>();
      pathFromTop.add(topLevel.id());
      if (topLevel.id().equals(target)) {
        return Optional.of(pathFromTop);
      }
      var found = walkHasPart(topLevel, target, pathFromTop);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static Optional<List<URI>> walkHasPart(
      Organization current, URI target, List<URI> pathSoFar) {
    if (isNull(current.hasPart())) {
      return Optional.empty();
    }
    for (var child : current.hasPart()) {
      var extended = new ArrayList<>(pathSoFar);
      extended.add(child.id());
      if (child.id().equals(target)) {
        return Optional.of(extended);
      }
      var deeper = walkHasPart(child, target, extended);
      if (deeper.isPresent()) {
        return deeper;
      }
    }
    return Optional.empty();
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
