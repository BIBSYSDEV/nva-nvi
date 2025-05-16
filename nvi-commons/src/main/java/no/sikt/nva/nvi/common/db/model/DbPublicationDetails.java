package no.sikt.nva.nvi.common.db.model;

import static java.util.Collections.emptyList;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationDetails.Builder.class)
public record DbPublicationDetails(
    URI id,
    URI publicationBucketUri,
    DbPageCount pages,
    DbPublicationDate publicationDate,
    // FIXME: This field should be called "nviCreators", as other creators are not included
    @DynamoDbConvertedBy(DbCreatorTypeListConverter.class) List<DbCreatorType> creators,
    List<DbOrganization> topLevelNviOrganizations,
    Instant modifiedDate,
    int contributorCount,
    String abstractText,
    String identifier,
    String language,
    String status,
    String title) {

  public DbPublicationDetails {
    creators = Optional.ofNullable(creators).map(List::copyOf).orElse(emptyList());
    topLevelNviOrganizations =
        Optional.ofNullable(topLevelNviOrganizations).map(List::copyOf).orElse(emptyList());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private URI publicationBucketUri;
    private DbPageCount pages;
    private DbPublicationDate publicationDate;
    private List<DbCreatorType> creators;
    private List<DbOrganization> topLevelNviOrganizations;
    private Instant modifiedDate;
    private int contributorCount;
    private String abstractText;
    private String identifier;
    private String language;
    private String status;
    private String title;

    private Builder() {}

    public Builder id(URI id) {
      this.id = id;
      return this;
    }

    public Builder publicationBucketUri(URI publicationBucketUri) {
      this.publicationBucketUri = publicationBucketUri;
      return this;
    }

    public Builder pages(DbPageCount pages) {
      this.pages = pages;
      return this;
    }

    public Builder publicationDate(DbPublicationDate publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder creators(List<DbCreatorType> creators) {
      this.creators = creators;
      return this;
    }

    public Builder topLevelNviOrganizations(List<DbOrganization> topLevelNviOrganizations) {
      this.topLevelNviOrganizations = topLevelNviOrganizations;
      return this;
    }

    public Builder modifiedDate(Instant modifiedDate) {
      this.modifiedDate = modifiedDate;
      return this;
    }

    public Builder contributorCount(int contributorCount) {
      this.contributorCount = contributorCount;
      return this;
    }

    public Builder abstractText(String abstractText) {
      this.abstractText = abstractText;
      return this;
    }

    public Builder identifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder language(String language) {
      this.language = language;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public DbPublicationDetails build() {
      return new DbPublicationDetails(
          id,
          publicationBucketUri,
          pages,
          publicationDate,
          creators,
          topLevelNviOrganizations,
          modifiedDate,
          contributorCount,
          abstractText,
          identifier,
          language,
          status,
          title);
    }
  }
}
