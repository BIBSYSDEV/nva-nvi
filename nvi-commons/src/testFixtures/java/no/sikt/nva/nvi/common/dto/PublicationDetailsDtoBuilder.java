package no.sikt.nva.nvi.common.dto;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_RANGE_AS_DTO;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;

public final class PublicationDetailsDtoBuilder {

  private URI id;
  private String identifier;
  private String title;
  private String status;
  private String language;
  private String abstractText;
  private PageCountDto pageCount;
  private PublicationDateDto publicationDate;
  private boolean isApplicable;
  private int creatorCount;
  private Instant modifiedDate;

  public PublicationDetailsDtoBuilder() {}

  public PublicationDetailsDtoBuilder(PublicationDetailsDto other) {
    this.id = other.id();
    this.identifier = other.identifier();
    this.title = other.title();
    this.status = other.status();
    this.language = other.language();
    this.abstractText = other.abstractText();
    this.pageCount = other.pageCount();
    this.publicationDate = other.publicationDate();
    this.isApplicable = other.isApplicable();
    this.creatorCount = other.creatorCount();
    this.modifiedDate = other.modifiedDate();
  }

  public static PublicationDetailsDtoBuilder randomPublicationDetailsDtoBuilder() {
    return builder()
        .withId(randomUri())
        .withIdentifier(randomUUID().toString())
        .withTitle(randomString())
        .withStatus("PUBLISHED")
        .withLanguage(null)
        .withAbstractText(randomString())
        .withPageCount(PAGE_RANGE_AS_DTO)
        .withPublicationDate(getRandomDateInCurrentYearAsDto())
        .withIsApplicable(true)
        .withCreatorCount(randomInteger())
        .withModifiedDate(Instant.now());
  }

  public static PublicationDetailsDto randomPublicationDetailsDto() {
    return randomPublicationDetailsDtoBuilder().build();
  }

  public static PublicationDetailsDtoBuilder builder() {
    return new PublicationDetailsDtoBuilder();
  }

  public PublicationDetailsDtoBuilder withId(URI id) {
    this.id = id;
    return this;
  }

  public PublicationDetailsDtoBuilder withIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public PublicationDetailsDtoBuilder withTitle(String title) {
    this.title = title;
    return this;
  }

  public PublicationDetailsDtoBuilder withStatus(String status) {
    this.status = status;
    return this;
  }

  public PublicationDetailsDtoBuilder withLanguage(String language) {
    this.language = language;
    return this;
  }

  public PublicationDetailsDtoBuilder withAbstractText(String abstractText) {
    this.abstractText = abstractText;
    return this;
  }

  public PublicationDetailsDtoBuilder withPageCount(PageCountDto pageCount) {
    this.pageCount = pageCount;
    return this;
  }

  public PublicationDetailsDtoBuilder withPublicationDate(PublicationDateDto publicationDate) {
    this.publicationDate = publicationDate;
    return this;
  }

  public PublicationDetailsDtoBuilder withIsApplicable(boolean isApplicable) {
    this.isApplicable = isApplicable;
    return this;
  }

  public PublicationDetailsDtoBuilder withCreatorCount(int creatorCount) {
    this.creatorCount = creatorCount;
    return this;
  }

  public PublicationDetailsDtoBuilder withModifiedDate(Instant modifiedDate) {
    this.modifiedDate = modifiedDate;
    return this;
  }

  public PublicationDetailsDto build() {
    return new PublicationDetailsDto(
        id,
        identifier,
        title,
        status,
        language,
        abstractText,
        pageCount,
        publicationDate,
        isApplicable,
        creatorCount,
        modifiedDate);
  }
}
