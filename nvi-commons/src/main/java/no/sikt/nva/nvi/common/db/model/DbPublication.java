package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublication.Builder.class)
public record DbPublication(
    URI id,
    String identifier,
    String title,
    String status,
    String language,
    String abstractText,
    DbPages pages,
    DbPublicationDate publicationDate,
    InstanceType publicationType,
    boolean isApplicable,
    boolean isInternationalCollaboration,
    List<DbPublicationChannel> publicationChannels,
    List<DbContributor> contributors,
    List<DbOrganization> topLevelOrganizations,
    Instant modifiedDate) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI builderId;
    private String builderIdentifier;
    private String builderTitle;
    private String builderStatus;
    private String builderLanguage;
    private String builderAbstractText;
    private DbPages builderPages;
    private DbPublicationDate builderPublicationDate;
    private InstanceType builderPublicationType;
    private boolean builderIsApplicable;
    private boolean builderIsInternationalCollaboration;
    private List<DbPublicationChannel> builderPublicationChannels;
    private List<DbContributor> builderContributors;
    private List<DbOrganization> builderTopLevelOrganizations;
    private Instant builderModifiedDate;

    private Builder() {}

    public Builder id(URI id) {
      this.builderId = id;
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

    public Builder applicable(boolean isApplicable) {
      this.builderIsApplicable = isApplicable;
      return this;
    }

    public Builder internationalCollaboration(boolean isInternationalCollaboration) {
      this.builderIsInternationalCollaboration = isInternationalCollaboration;
      return this;
    }

    public Builder publicationChannels(List<DbPublicationChannel> publicationChannels) {
      this.builderPublicationChannels = List.copyOf(publicationChannels);
      return this;
    }

    public Builder contributors(List<DbContributor> contributors) {
      this.builderContributors = List.copyOf(contributors);
      return this;
    }

    public Builder topLevelOrganizations(List<DbOrganization> topLevelOrganizations) {
      this.builderTopLevelOrganizations = List.copyOf(topLevelOrganizations);
      return this;
    }

    public Builder modifiedDate(Instant modifiedDate) {
      this.builderModifiedDate = modifiedDate;
      return this;
    }

    public DbPublication build() {
      return new DbPublication(
          builderId,
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
          builderPublicationChannels,
          builderContributors,
          builderTopLevelOrganizations,
          builderModifiedDate);
    }
  }
}
