package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbReportingStatus.Builder.class)
public record DbReportingStatus(URI institutionId,
                                DbNviPeriod nviPeriod,
                                DbCompletionStatus status,
                                Instant updatedDate) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI builderInstitutionId;
        private DbNviPeriod builderNviPeriod;
        private DbCompletionStatus builderStatus;
        private Instant builderUpdatedDate;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.builderInstitutionId = institutionId;
            return this;
        }

        public Builder nviPeriod(DbNviPeriod nviPeriod) {
            this.builderNviPeriod = nviPeriod;
            return this;
        }

        public Builder status(DbCompletionStatus status) {
            this.builderStatus = status;
            return this;
        }

        public Builder updatedDate(Instant updatedDate) {
            this.builderUpdatedDate = updatedDate;
            return this;
        }

        public DbReportingStatus build() {
            return new DbReportingStatus(builderInstitutionId, builderNviPeriod, builderStatus, builderUpdatedDate);
        }
    }
}
