package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;

public record UnverifiedNviCreator(String name, List<NviOrganization> nviAffiliations) implements NviCreator {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<URI> nviAffiliationsIds() {
        return nviAffiliations
                   .stream()
                   .map(NviOrganization::id)
                   .toList();
    }

    @Override
    public boolean isAffiliatedWith(URI institutionId) {
        return nviAffiliations
                   .stream()
                   .anyMatch(affiliation -> affiliation.isPartOf(institutionId));
    }

    @Override
    public List<URI> getAffiliationsPartOf(URI institutionId) {
        return nviAffiliations
                   .stream()
                   .filter(affiliation -> affiliation.isPartOf(institutionId))
                   .map(NviOrganization::id)
                   .toList();
    }

    public UnverifiedNviCreatorDto toDto() {
        return UnverifiedNviCreatorDto
                   .builder()
                   .withName(name)
                   .withAffiliations(nviAffiliationsIds())
                   .build();
    }

    public static final class Builder {

        private String name;

        private List<NviOrganization> nviAffiliations;

        private Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withNviAffiliations(List<NviOrganization> nviAffiliations) {
            this.nviAffiliations = nviAffiliations;
            return this;
        }

        public UnverifiedNviCreator build() {
            return new UnverifiedNviCreator(name, nviAffiliations);
        }
    }
}
