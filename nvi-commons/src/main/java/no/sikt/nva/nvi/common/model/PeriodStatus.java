package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PeriodStatus {
  OPEN("OpenPeriod"),
  CLOSED("ClosedPeriod"),
  NONE("NoPeriod"),
  UNOPENED("UnopenedPeriod");

  private final String value;

  PeriodStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
