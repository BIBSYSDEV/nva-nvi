package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;

public record VerifiedNviCreator(URI id, List<NviOrganization> nviAffiliations) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI id;

        private List<NviOrganization> nviAffiliations;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withNviAffiliations(List<NviOrganization> nviAffiliations) {
            this.nviAffiliations = nviAffiliations;
            return this;
        }

        public VerifiedNviCreator build() {
            return new VerifiedNviCreator(id, nviAffiliations);
        }
    }

    public record NviOrganization(URI id, NviOrganization topLevelOrganization) {

        public static NviOrganization.Builder builder() {
            return new NviOrganization.Builder();
        }

        public static final class Builder {

            private URI id;

            private VerifiedNviCreator.NviOrganization topLevelOrganization;

            private Builder() {
            }

            public Builder withId(URI id) {
                this.id = id;
                return this;
            }

            public Builder withTopLevelOrganization(NviOrganization topLevelOrganization) {
                this.topLevelOrganization = topLevelOrganization;
                return this;
            }
            public NviOrganization build() {
                return new NviOrganization(id, topLevelOrganization);
            }
        }

    }
}
