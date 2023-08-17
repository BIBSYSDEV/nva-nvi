package no.sikt.nva.nvi.common.model.events;

import java.net.URI;
import nva.commons.core.JacocoGenerated;


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