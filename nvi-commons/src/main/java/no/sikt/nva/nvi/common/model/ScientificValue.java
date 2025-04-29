package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

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

  public static ScientificValue parse(String value) {
    return Arrays.stream(values())
        .filter(scientificValue -> scientificValue.getValue().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow();
  }

  public boolean isValid() {
    return this == LEVEL_ONE || this == LEVEL_TWO;
  }

  public String getValue() {
    return value;
  }
}
