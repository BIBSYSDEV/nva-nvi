package no.sikt.nva.nvi.common.db.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublication.Builder.class)
public record DbPublication(
    URI id,
    URI publicationBucketUri,
    String identifier,
    String title,
    String status,
    String language,
    String abstractText,
    DbPages pages,
    DbPublicationDate publicationDate,
    InstanceType publicationType,
    Boolean applicable, // FIXME: Can we use boolean?
    Boolean internationalCollaboration,
    DbPublicationChannel publicationChannel,
    @DynamoDbConvertedBy(DbCreatorTypeListConverter.class) List<DbCreatorType> creators,
    int contributorCount,
    List<DbOrganization> topLevelOrganizations,
    Instant modifiedDate) {

  public static Builder builder() {
    return new Builder();
  }

  @DynamoDbIgnore
  public Builder copy() {
    return builder()
        .id(id)
        .publicationBucketUri(publicationBucketUri)
        .identifier(identifier)
        .title(title)
        .status(status)
        .language(language)
        .abstractText(abstractText)
        .pages(pages)
        .publicationDate(publicationDate)
        .publicationType(publicationType)
        .applicable(applicable)
        .internationalCollaboration(internationalCollaboration)
        .publicationChannel(publicationChannel)
        .contributorCount(contributorCount)
        .creators(creators)
        .topLevelOrganizations(topLevelOrganizations)
        .modifiedDate(modifiedDate);
  }

  public static final class Builder {

    private URI builderId;
    private URI builderPublicationBucketUri;
    private String builderIdentifier;
    private String builderTitle;
    private String builderStatus;
    private String builderLanguage;
    private String builderAbstractText;
    private DbPages builderPages;
    private DbPublicationDate builderPublicationDate;
    private InstanceType builderPublicationType;
    private Boolean builderIsApplicable;
    private Boolean builderIsInternationalCollaboration;
    private DbPublicationChannel builderPublicationChannel;
    private int builderContributorCount;
    private List<DbCreatorType> builderCreators = emptyList();
    private List<DbOrganization> builderTopLevelOrganizations = emptyList();
    private Instant builderModifiedDate;

    private Builder() {}

    public Builder id(URI id) {
      this.builderId = id;
      return this;
    }

    public Builder publicationBucketUri(URI publicationBucketUri) {
      this.builderPublicationBucketUri = publicationBucketUri;
      return this;
    }

    public Builder identifier(String identifier) {
      this.builderIdentifier = identifier;
      return this;
    }

    public Builder title(String title) {
      this.builderTitle = title;
      return this;
    }

    public Builder status(String status) {
      this.builderStatus = status;
      return this;
    }

    public Builder language(String language) {
      this.builderLanguage = language;
      return this;
    }

    public Builder abstractText(String abstractText) {
      this.builderAbstractText = abstractText;
      return this;
    }

    public Builder pages(DbPages pages) {
      this.builderPages = pages;
      return this;
    }

    public Builder publicationDate(DbPublicationDate publicationDate) {
      this.builderPublicationDate = publicationDate;
      return this;
    }

    public Builder publicationType(InstanceType publicationType) {
      this.builderPublicationType = publicationType;
      return this;
    }

    public Builder applicable(Boolean applicable) {
      this.builderIsApplicable = applicable;
      return this;
    }

    public Builder internationalCollaboration(Boolean internationalCollaboration) {
      this.builderIsInternationalCollaboration = internationalCollaboration;
      return this;
    }

    // FIXME
    @DynamoDbIgnore
    public Builder publicationChannels(List<DbPublicationChannel> publicationChannels) {
      if (nonNull(publicationChannels) && !publicationChannels.isEmpty()) {
        this.builderPublicationChannel = publicationChannels.getFirst();
      }
      return this;
    }

    public Builder publicationChannel(DbPublicationChannel publicationChannel) {
      this.builderPublicationChannel = publicationChannel;
      return this;
    }

    public Builder contributorCount(int contributorCount) {
      this.builderContributorCount = contributorCount;
      return this;
    }

    public Builder creators(List<DbCreatorType> creators) {
      this.builderCreators = creators;
      return this;
    }

    public Builder topLevelOrganizations(List<DbOrganization> topLevelOrganizations) {
      this.builderTopLevelOrganizations = topLevelOrganizations;
      return this;
    }

    public Builder modifiedDate(Instant modifiedDate) {
      this.builderModifiedDate = modifiedDate;
      return this;
    }

    public DbPublication build() {
      return new DbPublication(
          builderId,
          builderPublicationBucketUri,
          builderIdentifier,
          builderTitle,
          builderStatus,
          builderLanguage,
          builderAbstractText,
          builderPages,
          builderPublicationDate,
          builderPublicationType,
          builderIsApplicable,
          builderIsInternationalCollaboration,
          builderPublicationChannel,
          builderCreators,
          builderContributorCount,
          builderTopLevelOrganizations,
          builderModifiedDate);
    }
  }
}
