package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
  OPEN_PERIOD("OpenPeriod"),
  CLOSED_PERIOD("ClosedPeriod"),
  NO_PERIOD("NoPeriod"),
  UNOPENED_PERIOD("UnopenedPeriod");

  private final String value;

  Status(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
