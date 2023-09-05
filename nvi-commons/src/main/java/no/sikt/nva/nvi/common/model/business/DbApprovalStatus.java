package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbApprovalStatus.Builder.class)
public record DbApprovalStatus(URI institutionId,
                               DbStatus status,
                               DbUsername finalizedBy,
                               Instant finalizedDate) {

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbIgnore
    public Builder copy() {
        return builder()
                   .institutionId(institutionId)
                   .status(status)
                   .finalizedBy(finalizedBy)
                   .finalizedDate(finalizedDate);
    }

    public static final class Builder {

        private URI institutionId;
        private DbStatus status;
        private DbUsername finalizedBy;
        private Instant finalizedDate;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder status(DbStatus status) {
            this.status = status;
            return this;
        }

        public Builder finalizedBy(DbUsername finalizedBy) {
            this.finalizedBy = finalizedBy;
            return this;
        }

        public Builder finalizedDate(Instant finalizedDate) {
            this.finalizedDate = finalizedDate;
            return this;
        }

        public DbApprovalStatus build() {
            return new DbApprovalStatus(institutionId, status, finalizedBy, finalizedDate);
        }
    }
}