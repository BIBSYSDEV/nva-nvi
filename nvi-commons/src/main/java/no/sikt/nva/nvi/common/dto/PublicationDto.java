package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_COMMENTARY;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.common.utils.Validator.shouldBeTrue;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeEmpty;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.model.InstanceType;

// TODO Refactor to remove warnings NP-49938
@SuppressWarnings("PMD.TooManyFields")
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Publication")
public record PublicationDto(
    URI id,
    String identifier,
    String title,
    String status,
    String language,
    String abstractText,
    PageCountDto pageCount,
    PublicationDateDto publicationDate,
    InstanceType publicationType,
    InstanceType parentPublicationType,
    boolean isApplicable,
    boolean isInternationalCollaboration,
    Collection<PublicationChannelDto> publicationChannels,
    Collection<ContributorDto> contributors,
    Collection<Organization> topLevelOrganizations,
    Collection<String> isbnList,
    Instant modifiedDate) {

  public static final List<InstanceType> PUBLICATION_INSTANCE_TYPES_REQUIRING_ISBN =
      List.of(ACADEMIC_CHAPTER, ACADEMIC_COMMENTARY, ACADEMIC_MONOGRAPH);
  private static final List<InstanceType> INVALID_PARENT_PUBLICATION_TYPES_FOR_ACADEMIC_CHAPTER =
      List.of(ACADEMIC_COMMENTARY, ACADEMIC_MONOGRAPH);

  public PublicationDto {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(status, "Required field 'status' is null");

    publicationChannels = requireNonNullElse(publicationChannels, emptyList());
    contributors = requireNonNullElse(contributors, emptyList());
    topLevelOrganizations = requireNonNullElse(topLevelOrganizations, emptyList());
    isbnList = requireNonNullElse(isbnList, emptyList());
  }

  public void validate() {
    shouldNotBeNull(publicationDate, "Required field 'publicationDate' is null");
    shouldNotBeNull(publicationType, "Required field 'publicationType' is null");
    shouldNotBeEmpty(publicationChannels, "Required field 'publicationChannels' is empty");
    shouldNotBeEmpty(contributors, "Required field 'contributors' is empty");
    shouldNotBeEmpty(topLevelOrganizations, "Required field 'topLevelOrganizations' is empty");

    shouldBeTrue(publicationType().isValid(), "Required field 'publicationType' is invalid");
    validateIsbnWhenRequired();
    validateParentPublicationType();
    contributors.forEach(ContributorDto::validate);
  }

  private void validateParentPublicationType() {
    if (ACADEMIC_CHAPTER.equals(publicationType()) && parentPublicationTypeIsNotSupported()) {
      throw new ValidationException(
          "AcademicChapter is not valid nvi candidate when it is part of %s"
              .formatted(parentPublicationType()));
    }
  }

  private boolean parentPublicationTypeIsNotSupported() {
    return Optional.ofNullable(parentPublicationType())
        .map(INVALID_PARENT_PUBLICATION_TYPES_FOR_ACADEMIC_CHAPTER::contains)
        .orElse(false);
  }

  public static PublicationDto from(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, PublicationDto.class);
  }

  public static Builder builder() {
    return new Builder();
  }

  private void validateIsbnWhenRequired() {
    if (PUBLICATION_INSTANCE_TYPES_REQUIRING_ISBN.contains(publicationType())) {
      shouldNotBeEmpty(
          isbnList(),
          "Required field 'isbnList' must not be empty for %s"
              .formatted(publicationType().getValue()));
    }
  }

  public static final class Builder {

    private URI id;
    private String identifier;
    private String title;
    private String status;
    private String language;
    private String abstractText;
    private PageCountDto pageCount;
    private PublicationDateDto publicationDate;
    private InstanceType publicationType;
    private InstanceType parentPublicationType;
    private boolean isApplicable;
    private boolean isInternationalCollaboration;
    private Collection<PublicationChannelDto> publicationChannels;
    private Collection<ContributorDto> contributors;
    private Collection<Organization> topLevelOrganizations;
    private Collection<String> isbnList;
    private Instant modifiedDate;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withIdentifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder withAbstract(String abstractText) {
      this.abstractText = abstractText;
      return this;
    }

    public Builder withPageCount(PageCountDto pageCount) {
      this.pageCount = pageCount;
      return this;
    }

    public Builder withPublicationDate(PublicationDateDto publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withStatus(String status) {
      this.status = status;
      return this;
    }

    public Builder withPublicationType(InstanceType publicationType) {
      this.publicationType = publicationType;
      return this;
    }

    public Builder withParentPublicationType(InstanceType parentPublicationType) {
      this.parentPublicationType = parentPublicationType;
      return this;
    }

    public Builder withLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder withIsApplicable(boolean isApplicable) {
      this.isApplicable = isApplicable;
      return this;
    }

    public Builder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
      this.isInternationalCollaboration = isInternationalCollaboration;
      return this;
    }

    public Builder withPublicationChannels(Collection<PublicationChannelDto> publicationChannels) {
      this.publicationChannels = publicationChannels;
      return this;
    }

    public Builder withContributors(Collection<ContributorDto> contributors) {
      this.contributors = contributors;
      return this;
    }

    public Builder withTopLevelOrganizations(Collection<Organization> topLevelOrganizations) {
      this.topLevelOrganizations = topLevelOrganizations;
      return this;
    }

    public Builder withIsbnList(Collection<String> isbnList) {
      this.isbnList = isbnList;
      return this;
    }

    public Builder withModifiedDate(Instant modifiedDate) {
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
          parentPublicationType,
          isApplicable,
          isInternationalCollaboration,
          publicationChannels,
          contributors,
          topLevelOrganizations,
          isbnList,
          modifiedDate);
    }
  }
}
