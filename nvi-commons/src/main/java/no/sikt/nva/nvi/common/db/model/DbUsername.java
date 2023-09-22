package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.model.DbUsername.Builder;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = Builder.class)
public class DbUsername {

    @JsonValue
    private final String value;

    private DbUsername(String value) {
        Objects.requireNonNull(value);
        this.value = value;
    }

    public static DbUsername fromString(String value) {
        return new DbUsername(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String value() {
        return value;
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (DbUsername) obj;
        return Objects.equals(this.value, that.value);
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
