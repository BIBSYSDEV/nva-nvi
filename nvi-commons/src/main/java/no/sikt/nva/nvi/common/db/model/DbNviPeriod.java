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

        private String publishingYear;
        private Instant reportingDate;
        private DbUsername createdBy;
        private DbUsername modifiedBy;

        private Builder() {
        }


        public Builder publishingYear(String publishingYear) {
            this.publishingYear = publishingYear;
            return this;
        }

        public Builder reportingDate(Instant reportingDate) {
            this.reportingDate = reportingDate;
            return this;
        }

        public Builder createdBy(DbUsername createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder modifiedBy(DbUsername modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }

        public DbNviPeriod build() {
            return new DbNviPeriod(publishingYear, reportingDate, createdBy, modifiedBy);
        }
    }
}
