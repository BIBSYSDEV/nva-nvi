package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_RANGE_AS_DTO;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.ScientificValue;

public class PublicationDtoBuilder {

  public static PublicationDto.Builder randomPublicationDtoBuilder() {
    var channel =
        PublicationChannelDto.builder()
            .withId(randomUri())
            .withChannelType(ChannelType.JOURNAL)
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .build();
    return PublicationDto.builder()
        .withId(randomUri())
        .withIdentifier(randomUUID().toString())
        .withContributors(emptyList())
        .withTopLevelOrganizations(List.of(randomOrganization().build()))
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
        .withIsInternationalCollaboration(false);
  }

  public static PublicationDto.Builder fromRequest(UpsertNviCandidateRequest request) {
    var original = request.publicationDetails();
    return PublicationDto.builder()
        .withId(original.id())
        .withIdentifier(original.identifier())
        .withContributors(List.copyOf(original.contributors()))
        .withTopLevelOrganizations(List.copyOf(original.topLevelOrganizations()))
        .withPublicationChannels(List.copyOf(original.publicationChannels()))
        .withPublicationType(original.publicationType())
        .withModifiedDate(original.modifiedDate())
        .withPageCount(original.pageCount())
        .withPublicationDate(original.publicationDate())
        .withAbstract(original.abstractText())
        .withLanguage(original.language())
        .withStatus(original.status())
        .withTitle(original.title())
        .withIsApplicable(original.isApplicable())
        .withIsInternationalCollaboration(original.isInternationalCollaboration());
  }
}
