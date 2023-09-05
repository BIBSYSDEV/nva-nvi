package no.sikt.nva.nvi.evaluator.model;

import java.net.URI;

public record NonNviCandidate(URI publicationId) implements CandidateType {

    public static final class Builder {

        private URI publicationId;

        public Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public NonNviCandidate build() {
            return new NonNviCandidate(publicationId);
        }
    }
}