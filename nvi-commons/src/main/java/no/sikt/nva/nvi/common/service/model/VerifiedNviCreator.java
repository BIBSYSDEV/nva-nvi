package no.sikt.nva.nvi.common.service.model;

import static nva.commons.core.StringUtils.isBlank;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;

public record VerifiedNviCreator(URI id, List<URI> affiliations) implements NviCreatorType {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public DbCreator toDao() {
        return new DbCreator(id, affiliations);
    }

    public static final class Builder {

        private URI id;
        private List<URI> affiliations = Collections.emptyList();

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withAffiliations(List<URI> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public VerifiedNviCreator build() {
            if (isBlank(id.toString())) {
                throw new IllegalStateException("ID cannot be null or blank");
            }
            return new VerifiedNviCreator(id, affiliations);
        }
    }
}
