package no.sikt.nva.nvi.common.service.model;

import static no.sikt.nva.nvi.common.utils.Validator.doesNotHaveNullValues;
import static no.sikt.nva.nvi.common.utils.Validator.isBefore;

import java.time.Instant;

public record UpdatePeriodRequest(
    Integer publishingYear, Instant startDate, Instant reportingDate, Username modifiedBy)
    implements no.sikt.nva.nvi.common.service.requests.UpdatePeriodRequest {

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void validate() {
    doesNotHaveNullValues(this);
    isBefore(startDate(), reportingDate());
  }

  public static final class Builder {

    private Integer publishingYear;
    private Instant startDate;
    private Instant reportingDate;
    private Username modifiedBy;

    private Builder() {}

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

    public Builder withModifiedBy(Username modifiedBy) {
      this.modifiedBy = modifiedBy;
      return this;
    }

    public UpdatePeriodRequest build() {
      return new UpdatePeriodRequest(publishingYear, startDate, reportingDate, modifiedBy);
    }
  }
}
