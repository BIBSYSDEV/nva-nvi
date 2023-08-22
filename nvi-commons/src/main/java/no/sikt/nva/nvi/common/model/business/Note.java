package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.dto.NoteDb;
import software.amazon.awssdk.services.s3.endpoints.internal.Not;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Note(Username user,
                   String text,
                   Instant createdDate) {
    public NoteDb toDb() {
        return new NoteDb.Builder()
                   .withUser(user == null ? null : user.toDb())
                   .withText(text)
                   .withCreatedDate(createdDate)
                   .build();
    }

    public static Note fromDb(NoteDb db) {
        return new Note.Builder()
                   .withUser(db.getUser() == null ? null : Username.fromDb(db.getUser()))
                   .withText(db.getText())
                   .withCreatedDate(db.getCreatedDate())
                   .build();
    }

    public static final class Builder {

        private Username user;
        private String text;
        private Instant createdDate;

        public Builder() {
        }

        public Builder withUser(Username user) {
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

        public Note build() {
            return new Note(user, text, createdDate);
        }
    }
}
