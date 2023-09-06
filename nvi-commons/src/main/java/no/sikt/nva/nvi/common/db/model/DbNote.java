package no.sikt.nva.nvi.common.db.model;

import java.time.Instant;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbNote.Builder.class)
public record DbNote(DbUsername user,
                     String text,
                     Instant createdDate) {

    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated //TODO use later :)
    public static final class Builder {

        private DbUsername user;
        private String text;
        private Instant createdDate;

        public Builder() {
        }

        public Builder withUser(DbUsername user) {
            this.user = user;
            return this;
        }

        public Builder withText(String text) {
            this.text = text;
            return this;
        }

        public Builder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public DbNote build() {
            return new DbNote(user, text, createdDate);
        }
    }
}
