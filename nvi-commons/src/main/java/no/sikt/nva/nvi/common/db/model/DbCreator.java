package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbCreator.Builder.class)
public record DbCreator(URI creatorId, List<URI> affiliations) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI builderCreatorId;
        private List<URI> builderAffiliations;

        private Builder() {
        }

        public Builder creatorId(URI creatorId) {
            this.builderCreatorId = creatorId;
            return this;
        }

        public Builder affiliations(List<URI> affiliations) {
            this.builderAffiliations = affiliations;
            return this;
        }

        public DbCreator build() {
            return new DbCreator(builderCreatorId, builderAffiliations);
        }
    }
}
