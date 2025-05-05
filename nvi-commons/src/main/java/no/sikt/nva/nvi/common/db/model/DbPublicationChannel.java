package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationChannel.Builder.class)
public record DbPublicationChannel(URI id, String channelType, String scientificValue) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI builderId;
    private String builderChannelType;
    private String builderScientificValue;

    private Builder() {}

    public Builder id(URI id) {
      this.builderId = id;
      return this;
    }

    public Builder channelType(String channelType) {
      this.builderChannelType = channelType;
      return this;
    }

    public Builder scientificValue(String scientificValue) {
      this.builderScientificValue = scientificValue;
      return this;
    }

    public DbPublicationChannel build() {
      return new DbPublicationChannel(builderId, builderChannelType, builderScientificValue);
    }
  }
}
