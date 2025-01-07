package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Deprecated
// This overlaps with VerifiedNviCreator defined in nvi-commons and should be removed.
public record VerifiedNviCreator(URI id, List<NviOrganization> nviAffiliations) implements NviCreatorType {

    public static Builder builder() {
        return new Builder();
    }

    public List<URI> nviAffiliationsIds() {
        return nviAffiliations.stream()
                              .map(NviOrganization::id)
                              .toList();
    }

    public boolean isAffiliatedWith(URI institutionId) {
        return nviAffiliations.stream()
                              .anyMatch(affiliation -> affiliation.isPartOf(institutionId));
    }

    public List<URI> getAffiliationsPartOf(URI institutionId) {
        return nviAffiliations.stream()
                              .filter(affiliation -> affiliation.isPartOf(institutionId))
                              .map(NviOrganization::id)
                              .toList();
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
}
