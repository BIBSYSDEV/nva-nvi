package no.sikt.nva.nvi.common.model.business;

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

        private URI institutionId;
        private DbNviPeriod nviPeriod;
        private DbCompletionStatus status;
        private Instant updatedDate;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder nviPeriod(DbNviPeriod nviPeriod) {
            this.nviPeriod = nviPeriod;
            return this;
        }

        public Builder status(DbCompletionStatus status) {
            this.status = status;
            return this;
        }

        public Builder updatedDate(Instant updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public DbReportingStatus build() {
            return new DbReportingStatus(institutionId, nviPeriod, status, updatedDate);
        }
    }
}
