package no.sikt.nva.nvi.events.model;

import static nva.commons.core.StringUtils.isBlank;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public record UnverifiedNviCreator(String name, List<URI> nviAffiliations) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private List<URI> nviAffiliations = Collections.emptyList();

        private Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withNviAffiliations(List<URI> nviAffiliations) {
            this.nviAffiliations = nviAffiliations;
            return this;
        }

        public UnverifiedNviCreator build() {
            if (isBlank(name)) {
                throw new IllegalStateException("Name cannot be null or blank");
            }
            return new UnverifiedNviCreator(name, nviAffiliations);
        }
    }
}
