package no.sikt.nva.nvi.common.model.business;

import java.math.BigDecimal;
import java.net.URI;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbInstitutionPoints.Builder.class)
public record DbInstitutionPoints(URI institutionId, BigDecimal points) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI institutionId;
        private BigDecimal points;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder points(BigDecimal points) {
            this.points = points;
            return this;
        }

        public DbInstitutionPoints build() {
            return new DbInstitutionPoints(institutionId, points);
        }
    }
}
