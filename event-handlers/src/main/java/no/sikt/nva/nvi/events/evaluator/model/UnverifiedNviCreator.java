package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import nva.commons.core.JacocoGenerated;

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

    // FIXME: Ignoring test coverage for now (not used yet)
    @Override
    @JacocoGenerated
    public boolean isAffiliatedWith(URI institutionId) {
        return nviAffiliations
                   .stream()
                   .anyMatch(isNviOrganization(institutionId));
    }

    // FIXME: Ignoring test coverage for now (not used yet)
    @Override
    @JacocoGenerated
    public List<URI> getAffiliationsPartOf(URI institutionId) {
        return nviAffiliations
                   .stream()
                   .filter(isNviOrganization(institutionId))
                   .map(NviOrganization::id)
                   .toList();
    }

    private static Predicate<NviOrganization> isNviOrganization(URI institutionId) {
        return affiliation -> affiliation.isPartOf(institutionId);
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
