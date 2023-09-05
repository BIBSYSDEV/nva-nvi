package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbCreator.Builder.class)
public record DbCreator(URI creatorId, List<URI> affiliations) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI creatorId;
        private List<URI> affiliations;

        private Builder() {
        }

        public Builder creatorId(URI creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        public Builder affiliations(List<URI> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public DbCreator build() {
            return new DbCreator(creatorId, affiliations);
        }
    }
}
