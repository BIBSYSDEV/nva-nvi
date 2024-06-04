package no.sikt.nva.nvi.common.service.model;

import static no.sikt.nva.nvi.common.utils.Validator.doesNotHaveNullValues;
import static no.sikt.nva.nvi.common.utils.Validator.hasInvalidLength;
import static no.sikt.nva.nvi.common.utils.Validator.isBefore;
import static no.sikt.nva.nvi.common.utils.Validator.isPassedDate;
import java.time.Instant;

public record CreatePeriodRequest(Integer publishingYear, Instant startDate, Instant reportingDate,
                                  Username createdBy)
    implements no.sikt.nva.nvi.common.service.requests.CreatePeriodRequest {

    public static final int EXPECTED_LENGTH = 4;

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void validate() throws IllegalArgumentException {
        doesNotHaveNullValues(this);
        hasInvalidLength(publishingYear(), EXPECTED_LENGTH);
        isBefore(startDate(), reportingDate());
        isPassedDate(startDate());
        isPassedDate(reportingDate());
    }

    public static final class Builder {

        private Integer publishingYear;
        private Instant startDate;
        private Instant reportingDate;
        private Username createdBy;

        private Builder() {
        }

        public Builder withPublishingYear(Integer publishingYear) {
            this.publishingYear = publishingYear;
            return this;
        }

        public Builder withStartDate(Instant startDate) {
            this.startDate = startDate;
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

        public CreatePeriodRequest build() {
            return new CreatePeriodRequest(publishingYear, startDate, reportingDate, createdBy);
        }
    }
}