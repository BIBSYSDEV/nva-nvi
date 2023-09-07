package no.sikt.nva.nvi.common.db.model;

import java.math.BigDecimal;
import java.net.URI;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbInstitutionPoints.Builder.class)
public record DbInstitutionPoints(URI institutionId, BigDecimal points) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI builderInstitutionId;
        private BigDecimal builderPoints;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.builderInstitutionId = institutionId;
            return this;
        }

        public Builder points(BigDecimal points) {
            this.builderPoints = points;
            return this;
        }

        public DbInstitutionPoints build() {
            return new DbInstitutionPoints(builderInstitutionId, builderPoints);
        }
    }
}
