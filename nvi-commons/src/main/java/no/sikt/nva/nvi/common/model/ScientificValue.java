package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.function.Predicate;

public enum ScientificValue {
  NON_CANDIDATE("NonCandidateLevel"),
  UNASSIGNED("Unassigned"),
  LEVEL_ZERO("LevelZero"),
  LEVEL_ONE("LevelOne"),
  LEVEL_TWO("LevelTwo");

  @JsonValue private final String value;

  ScientificValue(String value) {
    this.value = value;
  }

  /**
   * Parses a string value to a ScientificValue enum, ignoring case and allowing for both enum name
   * and enum value to be used as input. This is because existing data can be in either format.
   */
  @JsonCreator
  public static ScientificValue parse(String stringValue) {
    return Arrays.stream(values()).filter(matchesEnumValue(stringValue)).findFirst().orElseThrow();
  }

  public boolean isValid() {
    return this == LEVEL_ONE || this == LEVEL_TWO;
  }

  public String getValue() {
    return value;
  }

  private static Predicate<ScientificValue> matchesEnumValue(String stringValue) {
    return value ->
        value.name().equalsIgnoreCase(stringValue)
            || value.getValue().equalsIgnoreCase(stringValue);
  }
}
