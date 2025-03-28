package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import nva.commons.core.JacocoGenerated;

public enum ScientificValue {
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

  @JacocoGenerated // Tested in other modules
  public String getValue() {
    return value;
  }
}
