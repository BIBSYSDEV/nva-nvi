package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.CreatorDb.Builder;

public class CreatorDb implements WithCopy<Builder> {

    public static final String CREATOR_ID_FIELD = "creatorId";
    public static final String AFFILIATIONS_FIELD = "affiliations";
    @JsonProperty(CREATOR_ID_FIELD)
    private URI creatorId;
    @JsonProperty(AFFILIATIONS_FIELD)
    private List<URI> affiliations;

    public CreatorDb(URI creatorId, List<URI> affiliations) {
        this.creatorId = creatorId;
        this.affiliations = affiliations;
    }

    @Override
    public Builder copy() {
        return new Builder().withCreatorId(creatorId).withAffiliations(affiliations);
    }

    public URI getCreatorId() {
        return creatorId;
    }

    public List<URI> getAffiliations() {
        return affiliations;
    }

    public static final class Builder {

        private URI creatorId;
        private List<URI> affiliations;

        public Builder() {
        }

        public Builder withCreatorId(URI creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        public Builder withAffiliations(List<URI> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public CreatorDb build() {
            return new CreatorDb(creatorId, affiliations);
        }
    }
}
