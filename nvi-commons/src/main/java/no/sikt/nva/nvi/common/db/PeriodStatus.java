package no.sikt.nva.nvi.common.db;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PeriodStatus(
    URI id, Instant startDate, Instant reportingDate, Status status, String year)
    implements JsonSerializable {

  public static Builder builder() {
    return new Builder();
  }

  public static PeriodStatus fromPeriod(DbNviPeriod period) {
    Objects.requireNonNull(period);
    if (isOpenPeriod(period)) {
      return toOpenPeriodStatus(period);
    }
    if (closedPeriod(period)) {
      return toClosedPeriodStatus(period);
    } else {
      return toUnopenedPeriod(period);
    }
  }

  @Override
  @JacocoGenerated
  public String toString() {
    return toJsonString();
  }

  public boolean isOpen() {
    return Status.OPEN_PERIOD.equals(status);
  }

  public boolean isClosed() {
    return Status.CLOSED_PERIOD.equals(status);
  }

  private static PeriodStatus toUnopenedPeriod(DbNviPeriod period) {
    return builder()
        .withId(period.id())
        .withStartDate(period.startDate())
        .withReportingDate(period.reportingDate())
        .withStatus(Status.UNOPENED_PERIOD)
        .withYear(period.publishingYear())
        .build();
  }

  private static boolean closedPeriod(DbNviPeriod period) {
    return period.reportingDate().isBefore(Instant.now());
  }

  private static boolean isOpenPeriod(DbNviPeriod period) {
    return period.startDate().isBefore(Instant.now())
        && period.reportingDate().isAfter(Instant.now());
  }

  private static PeriodStatus toOpenPeriodStatus(DbNviPeriod period) {
    return builder()
        .withId(period.id())
        .withStartDate(period.startDate())
        .withReportingDate(period.reportingDate())
        .withStatus(Status.OPEN_PERIOD)
        .withYear(period.publishingYear())
        .build();
  }

  private static PeriodStatus toClosedPeriodStatus(DbNviPeriod period) {
    return builder()
        .withId(period.id())
        .withStartDate(period.startDate())
        .withReportingDate(period.reportingDate())
        .withStatus(Status.CLOSED_PERIOD)
        .withYear(period.publishingYear())
        .build();
  }

  public enum Status {
    OPEN_PERIOD("OpenPeriod"),
    CLOSED_PERIOD("ClosedPeriod"),
    NO_PERIOD("NoPeriod"),
    UNOPENED_PERIOD("UnopenedPeriod");

    private final String value;

    Status(String value) {
      this.value = value;
    }

    @JacocoGenerated
    @JsonValue
    public String getValue() {
      return value;
    }
  }

  public static final class Builder {

    private Instant reportingDate;
    private Instant startDate;
    private Status status;
    private String year;
    private URI id;

    private Builder() {}

    public Builder withStartDate(Instant startDate) {
      this.startDate = startDate;
      return this;
    }

    public Builder withReportingDate(Instant reportingDate) {
      this.reportingDate = reportingDate;
      return this;
    }

    public Builder withStatus(Status status) {
      this.status = status;
      return this;
    }

    public Builder withYear(String year) {
      this.year = year;
      return this;
    }

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public PeriodStatus build() {
      return new PeriodStatus(id, startDate, reportingDate, status, year);
    }
  }
}
