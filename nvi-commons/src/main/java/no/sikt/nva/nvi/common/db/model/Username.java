package no.sikt.nva.nvi.common.db.model;

import static java.util.Objects.isNull;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = Username.Builder.class)
public record Username(String value) {

  public static Username fromString(String value) {
    return new Username(value);
  }

  public static Username fromUserName(no.sikt.nva.nvi.common.service.model.Username username) {
    if (isNull(username) || isNull(username.value())) {
      return null;
    }
    return new Username(username.value());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String builderValue;

    private Builder() {}

    public Builder value(String value) {
      this.builderValue = value;
      return this;
    }

    public Username build() {
      return fromString(builderValue);
    }
  }
}
