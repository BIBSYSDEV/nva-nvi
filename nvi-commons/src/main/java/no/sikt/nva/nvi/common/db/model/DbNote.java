package no.sikt.nva.nvi.common.db.model;

import java.time.Instant;
import java.util.UUID;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbNote.Builder.class)
public record DbNote(UUID noteId,
                     DbUsername user,
                     String text,
                     Instant createdDate) {

    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated //TODO use later :)
    public static final class Builder {

        private UUID builderNoteId;
        private DbUsername builderUser;
        private String builderText;
        private Instant builderCreatedDate;

        public Builder() {
        }

        public Builder noteId(UUID noteId) {
            this.builderNoteId = noteId;
            return this;
        }

        public Builder user(DbUsername user) {
            this.builderUser = user;
            return this;
        }

        public Builder text(String text) {
            this.builderText = text;
            return this;
        }

        public Builder createdDate(Instant createdDate) {
            this.builderCreatedDate = createdDate;
            return this;
        }

        public DbNote build() {
            return new DbNote(builderNoteId, builderUser, builderText, builderCreatedDate);
        }
    }
}
