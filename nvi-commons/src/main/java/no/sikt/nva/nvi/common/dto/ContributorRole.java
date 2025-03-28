package no.sikt.nva.nvi.common.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonValue;

public record ContributorRole(String value) {

  private static final String CREATOR = "Creator";

  public ContributorRole {
    if (isBlank(value)) {
      throw new IllegalArgumentException("Contributor role cannot be blank");
    }
  }

  public boolean isCreator() {
    return CREATOR.equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
