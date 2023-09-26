package no.sikt.nva.nvi.common.db.model;

import no.sikt.nva.nvi.common.db.model.DbUsername.Builder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = Builder.class)
public record DbUsername(
    String value

) {

    public static DbUsername fromString(String value) {
        return new DbUsername(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String builderValue;

        private Builder() {
        }

        public Builder value(String value) {
            this.builderValue = value;
            return this;
        }

        public DbUsername build() {
            return DbUsername.fromString(builderValue);
        }
    }
}
