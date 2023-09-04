package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviPeriod(String publishingYear,
                        Instant reportingDate,
                        Username createdBy,
                        Username modifiedBy) implements DynamoDbModel<NviPeriod> {

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder()
                   .withPublishingYear(publishingYear)
                   .withReportingDate(reportingDate)
                   .withCreatedBy(createdBy)
                   .withModifiedBy(modifiedBy);
    }

    public static final class Builder {

        private String publishingYear;
        private Instant reportingDate;
        private Username createdBy;
        private Username modifiedBy;

        public Builder() {
        }

        public Builder withPublishingYear(String publishingYear) {
            this.publishingYear = publishingYear;
            return this;
        }

        public Builder withReportingDate(Instant reportingDate) {
            this.reportingDate = reportingDate;
            return this;
        }

        public Builder withCreatedBy(Username createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder withModifiedBy(Username modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }

        public NviPeriod build() {
            return new NviPeriod(publishingYear, reportingDate, createdBy, modifiedBy);
        }
    }
}
