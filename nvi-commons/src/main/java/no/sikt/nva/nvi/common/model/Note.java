package no.sikt.nva.nvi.common.model;

public class Note {

    private final Username user;
    private final String text;

    public Note(Builder builder) {
        this.user = builder.user;
        this.text = builder.text;
    }

    public Username getUser() {
        return user;
    }

    public String getText() {
        return text;
    }

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
            return new Note(this);
        }
    }
}
