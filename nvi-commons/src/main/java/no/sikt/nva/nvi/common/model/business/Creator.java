package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.Set;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated //Getters not in use yet
public record Creator(URI creatorId, Set<URI> affiliations) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI creatorId;
        private Set<URI> affiliations;

        private Builder() {
        }

        public Builder withCreatorId(URI creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        public Builder withAffiliations(Set<URI> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public Creator build() {
            return new Creator(creatorId, affiliations);
        }
    }
}
