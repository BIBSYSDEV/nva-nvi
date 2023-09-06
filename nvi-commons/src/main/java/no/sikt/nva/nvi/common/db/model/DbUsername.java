package no.sikt.nva.nvi.common.db.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbUsername.Builder.class)
public record DbUsername(String value) {

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

        public DbUsername build() {
            return new DbUsername(value);
        }
    }
}
