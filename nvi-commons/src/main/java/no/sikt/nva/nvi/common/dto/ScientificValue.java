package no.sikt.nva.nvi.common.dto;

import com.fasterxml.jackson.annotation.JsonValue;
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

  public boolean isValid() {
    return this == LEVEL_ONE || this == LEVEL_TWO;
  }

  @JacocoGenerated // Tested in other modules
  public String getValue() {
    return value;
  }
}
