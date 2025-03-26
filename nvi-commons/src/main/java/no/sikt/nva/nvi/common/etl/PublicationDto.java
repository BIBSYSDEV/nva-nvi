package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Publication")
public record PublicationDto(
    URI id,
    String identifier,
    String title,
    String status,
    String language,
    PublicationDateDto publicationDate,
    String publicationType,
    boolean isApplicable,
    boolean isInternationalCollaboration,
    Collection<PublicationChannelDto> publicationChannels,
    Collection<ContributorDto> contributors,
    Collection<Organization> topLevelOrganizations,
    Instant modifiedDate) {

  public PublicationDto {
    requireNonNull(id, "Required field 'id' is null");
  }

  public void validate() {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(identifier, "Required field 'identifier' is null");
    requireNonNull(status, "Required field 'status' is null");
    requireNonNull(publicationDate, "Required field 'publicationDate' is null");
    requireNonNull(publicationType, "Required field 'publicationType' is null");
    requireNonNull(publicationChannels, "Required field 'publicationChannels' is null");
    requireNonNull(contributors, "Required field 'contributors' is null");
    requireNonNull(topLevelOrganizations, "Required field 'topLevelOrganizations' is null");

    publicationChannels.forEach(PublicationChannelDto::validate);
    contributors.forEach(ContributorDto::validate);
  }

  public static PublicationDto from(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, PublicationDto.class);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String identifier;
    private String title;
    private String status;
    private String language;
    private PublicationDateDto publicationDate;
    private String publicationType;
    private boolean isApplicable;
    private boolean isInternationalCollaboration;
    private Collection<PublicationChannelDto> publicationChannels;
    private Collection<ContributorDto> contributors;
    private Collection<Organization> topLevelOrganizations;
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

    public Builder withPublicationDate(PublicationDateDto publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withStatus(String status) {
      this.status = status;
      return this;
    }

    public Builder withPublicationType(String publicationType) {
      this.publicationType = publicationType;
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
}
