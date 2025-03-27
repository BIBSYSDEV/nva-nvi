package no.sikt.nva.nvi.common.etl;

import com.fasterxml.jackson.annotation.JsonValue;

public record ContributorRole(String value) {

  private static final String CREATOR = "Creator";

  public boolean isCreator() {
    return CREATOR.equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
