package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JacocoGenerated //TODO use later :)
public record DbNote(DbUsername user,
                     String text,
                     Instant createdDate) {

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
