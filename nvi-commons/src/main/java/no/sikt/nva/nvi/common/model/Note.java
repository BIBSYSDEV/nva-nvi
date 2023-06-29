package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Note(Username user,
                   String text) {

    public static class Builder {

        private Username user;
        private String text;

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

        public Note build() {
            return new Note(user, text);
        }
    }
}
