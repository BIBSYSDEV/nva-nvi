package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.PUBLISHED;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public final class PublicationDtoFixtures {

  private PublicationDtoFixtures() {}

  /**
   * Builds a {@link PublicationDto} mirroring the candidate: contributors mirror the candidate's
   * NVI creators plus one non-NVI contributor, and the publication channel mirrors the candidate's
   * channel, so both the NVI and the enrichment mapping branches are exercised.
   */
  public static PublicationDto publicationDtoMirroring(Candidate candidate) {
    var details = candidate.publicationDetails();
    return PublicationDto.builder()
        .withId(details.publicationId())
        .withIdentifier(details.publicationIdentifier().toString())
        .withTitle(details.title())
        .withStatus(PUBLISHED)
        .withLanguage(details.language())
        .withPublicationType(candidate.getPublicationType())
        .withIsApplicable(candidate.isApplicable())
        .withIsInternationalCollaboration(nonNull(candidate.getCollaborationFactor()))
        .withPublicationDate(details.publicationDate().toDtoPublicationDate())
        .withTopLevelOrganizations(details.topLevelOrganizations())
        .withContributors(buildContributors(candidate))
        .withPublicationChannels(buildChannels(candidate))
        .withHandles(details.handles())
        .withModifiedDate(Instant.now())
        .build();
  }

  private static List<ContributorDto> buildContributors(Candidate candidate) {
    var details = candidate.publicationDetails();
    var contributors = new ArrayList<ContributorDto>();
    details.verifiedCreators().stream()
        .map(PublicationDtoFixtures::contributorFor)
        .forEach(contributors::add);
    details.unverifiedCreators().stream()
        .map(PublicationDtoFixtures::contributorFor)
        .forEach(contributors::add);
    contributors.add(nonNviContributor(details.topLevelOrganizations()));
    return contributors;
  }

  private static ContributorDto contributorFor(NviCreator creator) {
    var affiliations =
        creator.nviAffiliations().stream()
            .map(uri -> Organization.builder().withId(uri).build())
            .toList();
    return ContributorDto.builder()
        .withId(creator.id())
        .withName(creator.name())
        .withRole(ContributorRole.CREATOR)
        .withAffiliations(affiliations)
        .build();
  }

  private static ContributorDto nonNviContributor(Collection<Organization> topLevelOrganizations) {
    var someAffiliation =
        topLevelOrganizations.stream()
            .findFirst()
            .orElseGet(() -> Organization.builder().withId(null).build());
    return ContributorDto.builder()
        .withName(randomString())
        .withRole(ContributorRole.EDITOR)
        .withAffiliations(List.of(someAffiliation))
        .build();
  }

  private static List<PublicationChannelDto> buildChannels(Candidate candidate) {
    var channel = candidate.getPublicationChannel();
    var builder = PublicationChannelDto.builder().withScientificValue(channel.scientificValue());
    if (nonNull(channel.id())) {
      builder.withId(channel.id());
    }
    if (nonNull(channel.channelType())) {
      builder.withChannelType(channel.channelType());
    }
    builder.withName(randomString()).withPrintIssn(randomString());
    return List.of(builder.build());
  }
}
