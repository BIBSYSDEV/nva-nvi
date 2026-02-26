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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class PublicationDetailsFixtures {

  public static PublicationDto.Builder publicationDtoMatchingCandidate(Candidate candidate) {
    var contributors = buildContributorsFromCandidate(candidate);
    var channels = buildChannelsFromCandidate(candidate);
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus("Published")
        .withContributors(contributors)
        .withPublicationChannels(channels)
        .withTopLevelOrganizations(candidate.publicationDetails().topLevelOrganizations());
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
