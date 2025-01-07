package no.sikt.nva.nvi.common.service.dto;

import static nva.commons.core.StringUtils.isBlank;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;

public record UnverifiedNviCreatorDto(String name, List<URI> affiliations) implements NviCreatorDto {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public DbUnverifiedCreator toDao() {
        return new DbUnverifiedCreator(name, affiliations);
    }

    public static final class Builder {

        private String name;
        private List<URI> affiliations = Collections.emptyList();

        private Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withAffiliations(List<URI> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public UnverifiedNviCreatorDto build() {
            if (isBlank(name)) {
                throw new IllegalStateException("Name cannot be null or blank");
            }
            return new UnverifiedNviCreatorDto(name, affiliations);
        }
    }
}
