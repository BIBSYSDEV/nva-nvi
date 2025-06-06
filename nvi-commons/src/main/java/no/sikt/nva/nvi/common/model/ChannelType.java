package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ChannelType implements ParsableEnum {
  NON_CANDIDATE("NonCandidateChannelType"),
  JOURNAL("Journal"),
  SERIES("Series"),
  PUBLISHER("Publisher");

  private final String value;

  ChannelType(String value) {
    this.value = value;
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ChannelType parse(String stringValue) {
    return ParsableEnum.parseOrDefault(ChannelType.class, stringValue, NON_CANDIDATE);
  }

  public boolean isValid() {
    return this != NON_CANDIDATE;
  }
}
