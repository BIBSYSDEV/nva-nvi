package no.sikt.nva.nvi.evaluator.calculator;

public record ChannelLevel (String type, String level){

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type;
        private String level;

        private Builder() {
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withLevel(String level) {
            this.level = level;
            return this;
        }

        public ChannelLevel build() {
            return new ChannelLevel(type, level);
        }
    }
}
