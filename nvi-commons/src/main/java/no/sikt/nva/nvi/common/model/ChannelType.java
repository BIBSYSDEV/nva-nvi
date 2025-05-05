package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.function.Predicate;

public enum ChannelType {
  JOURNAL("Journal"),
  SERIES("Series"),
  PUBLISHER("Publisher");

  private final String value;

  ChannelType(String value) {
    this.value = value;
  }

  /**
   * Parses a string value to an enum, ignoring case and allowing for both enum name and enum value
   * to be used as input. This is because existing data can be in either format.
   */
  @JsonCreator
  public static ChannelType parse(String stringValue) {
    return Arrays.stream(values()).filter(matchesEnumValue(stringValue)).findFirst().orElseThrow();
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  private static Predicate<ChannelType> matchesEnumValue(String stringValue) {
    return value ->
        value.name().equalsIgnoreCase(stringValue)
            || value.getValue().equalsIgnoreCase(stringValue);
  }
}
