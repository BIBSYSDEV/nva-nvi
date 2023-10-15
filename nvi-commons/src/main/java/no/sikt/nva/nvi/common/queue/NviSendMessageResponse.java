package no.sikt.nva.nvi.common.queue;

public record NviSendMessageResponse(String messageId) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String builderMessageId;

        private Builder() {
        }

        public Builder messageId(String messageId) {
            this.builderMessageId = messageId;
            return this;
        }

        public NviSendMessageResponse build() {
            return new NviSendMessageResponse(builderMessageId);
        }
    }
}
