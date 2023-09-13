package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbApprovalStatus.Builder.class)
public record DbApprovalStatus(URI institutionId,
                               DbStatus status,
                               DbUsername assignee,
                               DbUsername finalizedBy,
                               Instant finalizedDate) {

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbIgnore
    public ApprovalStatusDao toDao(UUID candidateIdentifier) {
        return new ApprovalStatusDao(candidateIdentifier, this);
    }

    @DynamoDbIgnore
    public Builder copy() {
        return builder()
                   .institutionId(institutionId)
                   .status(status)
                   .assignee(assignee)
                   .finalizedBy(finalizedBy)
                   .finalizedDate(finalizedDate);
    }

    public static final class Builder {

        private URI builderInstitutionId;
        private DbStatus builderStatus;
        private DbUsername builderAssignee;
        private DbUsername builderFinalizedBy;
        private Instant builderFinalizedDate;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.builderInstitutionId = institutionId;
            return this;
        }

        public Builder status(DbStatus status) {
            this.builderStatus = status;
            return this;
        }

        public Builder assignee(DbUsername assignee) {
            this.builderAssignee = assignee;
            return this;
        }

        public Builder finalizedBy(DbUsername finalizedBy) {
            this.builderFinalizedBy = finalizedBy;
            return this;
        }

        public Builder finalizedDate(Instant finalizedDate) {
            this.builderFinalizedDate = finalizedDate;
            return this;
        }

        public DbApprovalStatus build() {
            return new DbApprovalStatus(builderInstitutionId, builderStatus, builderAssignee,
                                        builderFinalizedBy, builderFinalizedDate);
        }
    }
}