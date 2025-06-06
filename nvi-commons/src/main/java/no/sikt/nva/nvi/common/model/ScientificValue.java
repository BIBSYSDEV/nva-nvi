package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ScientificValue implements ParsableEnum {
  NON_CANDIDATE("NonCandidateLevel"),
  UNASSIGNED("Unassigned"),
  LEVEL_ZERO("LevelZero"),
  LEVEL_ONE("LevelOne"),
  LEVEL_TWO("LevelTwo");

  private final String value;

  ScientificValue(String value) {
    this.value = value;
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ScientificValue parse(String stringValue) {
    return ParsableEnum.parseOrDefault(ScientificValue.class, stringValue, NON_CANDIDATE);
  }

  public boolean isValid() {
    return this == LEVEL_ONE || this == LEVEL_TWO;
  }
}
