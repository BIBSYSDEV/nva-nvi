package no.sikt.nva.nvi.index.report.request;

import static java.util.Objects.nonNull;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum ReportType {
  AUTHOR_SHARES("author-shares"),
  AUTHOR_SHARES_CONTROL("author-shares-control"),
  PUBLICATION_POINTS("publication-points"),
  DEFAULT_REPORT(null);

  private final String value;

  ReportType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static ReportType fromValue(String candidate) {
    return Arrays.stream(values())
        .filter(value -> nonNull(value.getValue()))
        .filter(value -> value.getValue().equals(candidate))
        .findFirst()
        .orElse(DEFAULT_REPORT);
  }
}
