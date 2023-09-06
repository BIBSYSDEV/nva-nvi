package no.sikt.nva.nvi.common.db.model;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbNviPeriod.Builder.class)
public record DbNviPeriod(String publishingYear,
                          Instant reportingDate,
                          DbUsername createdBy,
                          DbUsername modifiedBy) {

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbIgnore
    public Builder copy() {
        return builder()
                   .publishingYear(publishingYear)
                   .reportingDate(reportingDate)
                   .createdBy(createdBy)
                   .modifiedBy(modifiedBy);
    }

    public static final class Builder {

        private String builderPublishingYear;
        private Instant builderReportingDate;
        private DbUsername builderCreatedBy;
        private DbUsername builderModifiedBy;

        private Builder() {
        }


        public Builder publishingYear(String publishingYear) {
            this.builderPublishingYear = publishingYear;
            return this;
        }

        public Builder reportingDate(Instant reportingDate) {
            this.builderReportingDate = reportingDate;
            return this;
        }

        public Builder createdBy(DbUsername createdBy) {
            this.builderCreatedBy = createdBy;
            return this;
        }

        public Builder modifiedBy(DbUsername modifiedBy) {
            this.builderModifiedBy = modifiedBy;
            return this;
        }

        public DbNviPeriod build() {
            return new DbNviPeriod(builderPublishingYear, builderReportingDate, builderCreatedBy, builderModifiedBy);
        }
    }
}
