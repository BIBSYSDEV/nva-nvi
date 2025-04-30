package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.function.Predicate;

public enum ScientificValue {
  NON_CANDIDATE("NonCandidateLevel"),
  UNASSIGNED("Unassigned"),
  LEVEL_ZERO("LevelZero"),
  LEVEL_ONE("LevelOne"),
  LEVEL_TWO("LevelTwo");

  private final String value;

  ScientificValue(String value) {
    this.value = value;
  }

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
