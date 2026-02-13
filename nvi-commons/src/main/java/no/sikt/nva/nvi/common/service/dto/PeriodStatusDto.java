package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.net.URI;
import no.sikt.nva.nvi.common.model.PeriodStatus;

@JsonTypeName(PeriodStatusDto.NVI_PERIOD)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record PeriodStatusDto(
    URI id, PeriodStatus status, String startDate, String reportingDate, String year) {

  public static final String NVI_PERIOD = "NviReportingPeriod";

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private PeriodStatus status;
    private String startDate;
    private String closedDate;
    private String year;
    private URI id;

    private Builder() {}

    public Builder withStatus(PeriodStatus status) {
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
