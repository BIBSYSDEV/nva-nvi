package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbApprovalStatus.Builder.class)
public record DbApprovalStatus(URI institutionId,
                               Status status,
                               Username finalizedBy,
                               Instant finalizedDate) {

    private DbApprovalStatus(Builder builder) {
        this(builder.institutionId, builder.status, builder.finalizedBy, builder.finalizedDate);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy(){
        return builder()
                   .institutionId(institutionId)
                   .status(status)
                   .finalizedBy(finalizedBy)
                   .finalizedDate(finalizedDate);
    }

    public static final class Builder {

        private URI institutionId;
        private Status status;
        private Username finalizedBy;
        private Instant finalizedDate;

        private Builder() {
        }

        public DbApprovalStatus.Builder institutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public DbApprovalStatus.Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder finalizedBy(Username finalizedBy) {
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