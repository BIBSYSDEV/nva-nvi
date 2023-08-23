package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.NoteDb.Builder;
import no.sikt.nva.nvi.common.model.business.Username;

public class NoteDb implements WithCopy<Builder> {

    public static final String USER_FIELD = "user";
    public static final String TEXT_FIELD = "text";
    public static final String CREATED_FIELD = "createdDate";
    @JsonProperty(USER_FIELD)
    private UsernameDb user;
    @JsonProperty(TEXT_FIELD)
    private String text;

    @JsonProperty(CREATED_FIELD)
    private Instant createdDate;

    public NoteDb(UsernameDb user, String text, Instant createdDate) {
        this.user = user;
        this.text = text;
        this.createdDate = createdDate;
    }

    public NoteDb() {
    }

    @Override
    public Builder copy() {
        return new Builder().withUser(user).withText(text).withCreatedDate(createdDate);
    }

    public UsernameDb getUser() {
        return user;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public static final class Builder {

        private UsernameDb user;
        private String text;
        private Instant createdDate;

        public Builder() {
        }

        public Builder withUser(UsernameDb user) {
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

        public NoteDb build() {
            return new NoteDb(user, text, createdDate);
        }
    }
}
