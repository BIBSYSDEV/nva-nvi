package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import no.sikt.nva.nvi.common.model.ScientificValue;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationChannel.Builder.class)
public record DbPublicationChannel(
    URI id,
    String channelType,
    String identifier,
    String name,
    String year,
    ScientificValue scientificValue,
    String onlineIssn,
    String printIssn) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI builderId;
    private String builderChannelType;
    private String builderIdentifier;
    private String builderName;
    private String builderYear;
    private ScientificValue builderScientificValue;
    private String builderOnlineIssn;
    private String builderPrintIssn;

    private Builder() {}

    public static Builder aDbPublicationChannel() {
      return new Builder();
    }

    public Builder id(URI id) {
      this.builderId = id;
      return this;
    }

    public Builder channelType(String channelType) {
      this.builderChannelType = channelType;
      return this;
    }

    public Builder identifier(String identifier) {
      this.builderIdentifier = identifier;
      return this;
    }

    public Builder name(String name) {
      this.builderName = name;
      return this;
    }

    public Builder year(String year) {
      this.builderYear = year;
      return this;
    }

    public Builder scientificValue(ScientificValue scientificValue) {
      this.builderScientificValue = scientificValue;
      return this;
    }

    public Builder onlineIssn(String onlineIssn) {
      this.builderOnlineIssn = onlineIssn;
      return this;
    }

    public Builder printIssn(String printIssn) {
      this.builderPrintIssn = printIssn;
      return this;
    }

    public DbPublicationChannel build() {
      return new DbPublicationChannel(
          builderId,
          builderChannelType,
          builderIdentifier,
          builderName,
          builderYear,
          builderScientificValue,
          builderOnlineIssn,
          builderPrintIssn);
    }
  }
}
