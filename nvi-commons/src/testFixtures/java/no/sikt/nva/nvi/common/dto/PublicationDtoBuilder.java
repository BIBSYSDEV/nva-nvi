package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.model.InstanceType;

public class PublicationDtoBuilder {

  private URI id;
  private String identifier;
  private Collection<ContributorDto> contributors;
  private Collection<Organization> topLevelOrganizations;
  private Collection<PublicationChannelDto> publicationChannels;
  private InstanceType publicationType;
  private Instant modifiedDate;
  private PageCountDto pageCount;
  private PublicationDateDto publicationDate;
  private String abstractText;
  private String language;
  private String status;
  private String title;
  private boolean isApplicable;
  private boolean isInternationalCollaboration;

  // FIXME: CLean this up
  public static PublicationDtoBuilder randomPublicationDtoBuilder() {
    var channel =
        PublicationChannelDto.builder()
            .withId(randomUri())
            .withChannelType(ChannelType.JOURNAL.getValue())
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .build();
    return new PublicationDtoBuilder()
        .withId(randomUri())
        .withIdentifier(randomUUID().toString())
        .withContributors(emptyList()) // TODO
        .withTopLevelOrganizations(emptyList()) // TODO
        .withPublicationChannels(List.of(channel))
        .withPublicationType(InstanceType.ACADEMIC_ARTICLE)
        .withModifiedDate(Instant.now())
        .withPageCount(new PageCountDto("4", "5", null))
        .withPublicationDate(new PublicationDateDto(String.valueOf(CURRENT_YEAR), "01", "01"))
        .withAbstract(randomString())
        .withLanguage(null)
        .withStatus("PUBLISHED")
        .withTitle(randomString())
        .withIsApplicable(true)
        .withIsInternationalCollaboration(false);
  }

  public static PublicationDtoBuilder fromRequest(UpsertNviCandidateRequest request) {
    var original = request.publicationDetails();
    return new PublicationDtoBuilder()
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

  public PublicationDtoBuilder withId(URI id) {
    this.id = id;
    return this;
  }

  public PublicationDtoBuilder withIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public PublicationDtoBuilder withTitle(String title) {
    this.title = title;
    return this;
  }

  public PublicationDtoBuilder withAbstract(String abstractText) {
    this.abstractText = abstractText;
    return this;
  }

  public PublicationDtoBuilder withPageCount(PageCountDto pageCount) {
    this.pageCount = pageCount;
    return this;
  }

  public PublicationDtoBuilder withPublicationDate(PublicationDateDto publicationDate) {
    this.publicationDate = publicationDate;
    return this;
  }

  public PublicationDtoBuilder withStatus(String status) {
    this.status = status;
    return this;
  }

  public PublicationDtoBuilder withPublicationType(InstanceType publicationType) {
    this.publicationType = publicationType;
    return this;
  }

  public PublicationDtoBuilder withLanguage(String language) {
    this.language = language;
    return this;
  }

  public PublicationDtoBuilder withIsApplicable(boolean isApplicable) {
    this.isApplicable = isApplicable;
    return this;
  }

  public PublicationDtoBuilder withIsInternationalCollaboration(
      boolean isInternationalCollaboration) {
    this.isInternationalCollaboration = isInternationalCollaboration;
    return this;
  }

  public PublicationDtoBuilder withPublicationChannels(
      Collection<PublicationChannelDto> publicationChannels) {
    this.publicationChannels = publicationChannels;
    return this;
  }

  public PublicationDtoBuilder withContributors(Collection<ContributorDto> contributors) {
    this.contributors = contributors;
    return this;
  }

  public PublicationDtoBuilder withTopLevelOrganizations(
      Collection<Organization> topLevelOrganizations) {
    this.topLevelOrganizations = topLevelOrganizations;
    return this;
  }

  public PublicationDtoBuilder withModifiedDate(Instant modifiedDate) {
    this.modifiedDate = modifiedDate;
    return this;
  }

  public PublicationDto build() {
    return new PublicationDto(
        id,
        identifier,
        title,
        status,
        language,
        abstractText,
        pageCount,
        publicationDate,
        publicationType,
        isApplicable,
        isInternationalCollaboration,
        publicationChannels,
        contributors,
        topLevelOrganizations,
        modifiedDate);
  }
}
