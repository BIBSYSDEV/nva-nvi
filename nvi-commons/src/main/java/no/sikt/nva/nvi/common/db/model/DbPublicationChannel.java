package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationChannel.Builder.class)
public record DbPublicationChannel(URI id, String channelType, String scientificValue) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String channelType;
    private String scientificValue;

    private Builder() {}

    public Builder id(URI id) {
      this.id = id;
      return this;
    }

    public Builder channelType(String channelType) {
      this.channelType = channelType;
      return this;
    }

    public Builder scientificValue(String scientificValue) {
      this.scientificValue = scientificValue;
      return this;
    }

    public DbPublicationChannel build() {
      return new DbPublicationChannel(id, channelType, scientificValue);
    }
  }
}
