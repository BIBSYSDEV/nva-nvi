package no.sikt.nva.nvi.common.model.business;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = Username.Builder.class)
public record Username(String value) {

    public Username(Builder b) {
        this(b.value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String value;

        private Builder() {
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Username build() {
            return new Username(value);
        }
    }
}
