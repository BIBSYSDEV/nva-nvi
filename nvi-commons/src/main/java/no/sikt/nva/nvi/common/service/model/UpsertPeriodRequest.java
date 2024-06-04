package no.sikt.nva.nvi.common.service.model;

import java.time.Instant;

public abstract class UpsertPeriodRequest implements no.sikt.nva.nvi.common.service.requests.UpsertPeriodRequest {

    private final Integer publishingYear;
    private final Instant startDate;
    private final Instant reportingDate;
    private final Username createdBy;

    public UpsertPeriodRequest(Integer publishingYear, Instant startDate, Instant reportingDate,
                               Username createdBy) {
        this.publishingYear = publishingYear;
        this.startDate = startDate;
        this.reportingDate = reportingDate;
        this.createdBy = createdBy;
    }

    public Integer publishingYear() {
        return publishingYear;
    }

    public Instant startDate() {
        return startDate;
    }

    public Instant reportingDate() {
        return reportingDate;
    }

    public Username createdBy() {
        return createdBy;
    }

    public static Builder builder() {
        return new Builder();
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

        public UpdatePeriodRequest buildUpdateRequest() {
            return new UpdatePeriodRequest(publishingYear, startDate, reportingDate, createdBy);
        }

        public CreatePeriodRequest buildCreateRequest() {
            return new CreatePeriodRequest(publishingYear, startDate, reportingDate, createdBy);
        }
    }
}
