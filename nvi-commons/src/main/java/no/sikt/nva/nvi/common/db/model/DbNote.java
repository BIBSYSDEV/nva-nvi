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

        private UUID noteId;
        private DbUsername user;
        private String text;
        private Instant createdDate;

        public Builder() {
        }

        public Builder noteId(UUID noteId) {
            this.noteId = noteId;
            return this;
        }

        public Builder user(DbUsername user) {
            this.user = user;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder createdDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public DbNote build() {
            return new DbNote(noteId, user, text, createdDate);
        }
    }
}
