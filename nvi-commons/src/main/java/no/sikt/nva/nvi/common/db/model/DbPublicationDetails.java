package no.sikt.nva.nvi.common.db.model;

import static java.util.Collections.emptyList;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationDetails.Builder.class)
public record DbPublicationDetails(
    URI id,
    URI publicationBucketUri,
    String identifier,
    String title,
    String status,
    String language,
    String abstractText,
    DbPages pages,
    DbPublicationDate publicationDate,
    @DynamoDbConvertedBy(DbCreatorTypeListConverter.class) List<DbCreatorType> creators,
    int contributorCount,
    List<DbOrganization> topLevelOrganizations,
    Instant modifiedDate) {

  public static Builder builder() {
    return new Builder();
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

    public DbPublicationDetails build() {
      return new DbPublicationDetails(
          builderId,
          builderPublicationBucketUri,
          builderIdentifier,
          builderTitle,
          builderStatus,
          builderLanguage,
          builderAbstractText,
          builderPages,
          builderPublicationDate,
          builderCreators,
          builderContributorCount,
          builderTopLevelOrganizations,
          builderModifiedDate);
    }
  }
}
