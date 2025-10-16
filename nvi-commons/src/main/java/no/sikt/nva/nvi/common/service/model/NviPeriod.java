package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.model.Status;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.requests.UpdatePeriodRequest;

public record NviPeriod(
    URI id,
    Integer publishingYear,
    Instant startDate,
    Instant reportingDate,
    Username createdBy,
    Username modifiedBy) {
  private static final String VALIDATION_ERROR_MESSAGE = "Field cannot be null: %s";

  public NviPeriod {
    shouldNotBeNull(id, String.format(VALIDATION_ERROR_MESSAGE, "id"));
    shouldNotBeNull(publishingYear, String.format(VALIDATION_ERROR_MESSAGE, "publishingYear"));
    shouldNotBeNull(startDate, String.format(VALIDATION_ERROR_MESSAGE, "startDate"));
    shouldNotBeNull(reportingDate, String.format(VALIDATION_ERROR_MESSAGE, "reportingDate"));
    shouldNotBeNull(createdBy, String.format(VALIDATION_ERROR_MESSAGE, "createdBy"));
    if (reportingDate.isBefore(startDate)) {
      throw new ValidationException("reportingDate must be after startDate");
    }
  }

  public static NviPeriod fromDao(NviPeriodDao period) {
    var dbNviPeriod = period.nviPeriod();
    return builder()
        .withId(dbNviPeriod.id())
        .withPublishingYear(Integer.parseInt(dbNviPeriod.publishingYear()))
        .withStartDate(dbNviPeriod.startDate())
        .withReportingDate(dbNviPeriod.reportingDate())
        .withCreatedBy(Username.fromUserName(dbNviPeriod.createdBy()))
        .withModifiedBy(Username.fromUserName(dbNviPeriod.modifiedBy()))
        .build();
  }

  public NviPeriodDao toDao() {
    var dbPeriod = this.toDbObject();
    return NviPeriodDao.builder().identifier(dbPeriod.publishingYear()).nviPeriod(dbPeriod).build();
  }

  public NviPeriodDto toDto() {
    return new NviPeriodDto(
        id, publishingYear.toString(), startDate.toString(), reportingDate.toString());
  }

  public static PeriodStatusDto toPeriodStatusDto(Optional<NviPeriod> optionalPeriod) {
    var status = mapToPeriodStatus(optionalPeriod);
    return optionalPeriod
        .map(
            period ->
                PeriodStatusDto.builder()
                    .withId(period.id())
                    .withStatus(status)
                    .withYear(period.publishingYear().toString())
                    .withStartDate(period.startDate().toString())
                    .withReportingDate(period.reportingDate().toString())
                    .build())
        .orElseGet(() -> PeriodStatusDto.builder().withStatus(status).build());
  }

  private static Status mapToPeriodStatus(Optional<NviPeriod> period) {
    if (period.filter(NviPeriod::isOpen).isPresent()) {
      return Status.OPEN_PERIOD;
    }
    if (period.filter(NviPeriod::isClosed).isPresent()) {
      return Status.CLOSED_PERIOD;
    }
    if (period.isPresent()) {
      return Status.UNOPENED_PERIOD;
    }
    return Status.NO_PERIOD;
  }

  public boolean isClosed() {
    return nonNull(reportingDate) && reportingDate.isBefore(Instant.now());
  }

  public boolean isOpen() {
    var now = Instant.now();
    return nonNull(startDate)
        && startDate.isBefore(now)
        && nonNull(reportingDate)
        && reportingDate.isAfter(now);
  }

  public boolean hasPublishingYear(String year) {
    return String.valueOf(publishingYear).equals(year);
  }

  public static Builder builder() {
    return new Builder();
  }

  public NviPeriod updateWithRequest(UpdatePeriodRequest request) {
    return this.copy()
        .withStartDate(request.startDate())
        .withReportingDate(request.reportingDate())
        .withModifiedBy(request.modifiedBy())
        .build();
  }

  private Builder copy() {
    return builder()
        .withId(id)
        .withPublishingYear(publishingYear)
        .withStartDate(startDate)
        .withReportingDate(reportingDate)
        .withCreatedBy(createdBy)
        .withModifiedBy(modifiedBy);
  }

  private DbNviPeriod toDbObject() {
    return DbNviPeriod.builder()
        .id(id)
        .publishingYear(String.valueOf(publishingYear))
        .startDate(startDate)
        .reportingDate(reportingDate)
        .createdBy(no.sikt.nva.nvi.common.db.model.Username.fromUserName(createdBy))
        .modifiedBy(no.sikt.nva.nvi.common.db.model.Username.fromUserName(modifiedBy))
        .build();
  }

  public static final class Builder {

    private URI id;
    private Integer publishingYear;
    private Instant startDate;
    private Instant reportingDate;
    private Username createdBy;
    private Username modifiedBy;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
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

    public Builder withModifiedBy(Username modifiedBy) {
      this.modifiedBy = modifiedBy;
      return this;
    }

    public NviPeriod build() {
      return new NviPeriod(id, publishingYear, startDate, reportingDate, createdBy, modifiedBy);
    }
  }
}
