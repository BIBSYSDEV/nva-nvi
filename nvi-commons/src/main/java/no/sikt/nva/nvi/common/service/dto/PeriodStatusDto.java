package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import nva.commons.core.JacocoGenerated;

@JsonTypeName(PeriodStatusDto.NVI_PERIOD)
@JsonTypeInfo(use = Id.NAME, property = "type")
public record PeriodStatusDto(
    URI id, Status status, String startDate, String reportingDate, String year) {

  public static final String NVI_PERIOD = "NviReportingPeriod";

  public static Builder builder() {
    return new Builder();
  }

  public static PeriodStatusDto fromPeriodStatus(
      no.sikt.nva.nvi.common.db.PeriodStatus periodStatus) {
    return builder()
        .withId(periodStatus.id())
        .withStatus(Status.parse(periodStatus.status().getValue()))
        .withStartDate(toStartDate(periodStatus))
        .withReportingDate(toReportingDate(periodStatus))
        .withYear(periodStatus.year())
        .build();
  }

  private static String toReportingDate(no.sikt.nva.nvi.common.db.PeriodStatus periodStatus) {
    return Optional.of(periodStatus)
        .map(no.sikt.nva.nvi.common.db.PeriodStatus::reportingDate)
        .map(Object::toString)
        .orElse(null);
  }

  private static String toStartDate(no.sikt.nva.nvi.common.db.PeriodStatus periodStatus) {
    return Optional.of(periodStatus)
        .map(no.sikt.nva.nvi.common.db.PeriodStatus::startDate)
        .map(Object::toString)
        .orElse(null);
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

    public static Status parse(String value) {
      return Arrays.stream(values())
          .filter(status -> status.getValue().equalsIgnoreCase(value))
          .findFirst()
          .orElseThrow();
    }

    @JacocoGenerated
    @JsonValue
    public String getValue() {
      return value;
    }
  }

  public static final class Builder {

    private Status status;
    private String startDate;
    private String closedDate;
    private String year;
    private URI id;

    private Builder() {}

    public Builder withStatus(Status status) {
      this.status = status;
      return this;
    }

    public Builder withReportingDate(String reportingDate) {
      this.closedDate = reportingDate;
      return this;
    }

    public Builder withStartDate(String startDate) {
      this.startDate = startDate;
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

    public PeriodStatusDto build() {
      return new PeriodStatusDto(id, status, startDate, closedDate, year);
    }
  }
}
