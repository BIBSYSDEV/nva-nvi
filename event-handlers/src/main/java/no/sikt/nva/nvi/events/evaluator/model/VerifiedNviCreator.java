package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record VerifiedNviCreator(URI id, List<NviOrganization> nviAffiliations) {

    public static Builder builder() {
        return new Builder();
    }

    public List<URI> nviAffiliationsIds() {
        return nviAffiliations.stream()
                   .map(NviOrganization::id)
                   .collect(Collectors.toList());
    }

    public boolean isAffiliatedWith(URI institutionId) {
        return nviAffiliations.stream().anyMatch(affiliation -> affiliation.isPartOf(institutionId));
    }

    public List<URI> getAffiliationsPartOf(URI institutionId) {
        return nviAffiliations.stream()
                   .filter(affiliation -> affiliation.isPartOf(institutionId))
                   .map(NviOrganization::id)
                   .collect(Collectors.toList());
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

        @Override
        public NviOrganization topLevelOrganization() {
            return Objects.isNull(topLevelOrganization) ? this : topLevelOrganization;
        }

        public boolean isPartOf(URI institutionId) {
            return topLevelOrganization().id().equals(institutionId);
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
